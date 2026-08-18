# Dynamic Views

## M2 — Market candle and reconnect recovery

```mermaid
sequenceDiagram
    actor User
    participant UI as Market chart
    participant API as Market REST/STOMP gateway
    participant Stream as MarketDataStreamService
    participant Binance as Binance adapter
    participant DB as PostgreSQL candles

    User->>UI: Select symbol + timeframe
    UI->>API: GET /api/v1/market/candles
    API->>Binance: Historical klines
    Binance-->>API: Normalized closed Candles
    API->>DB: INSERT ON CONFLICT DO NOTHING
    API-->>UI: Historical snapshot
    UI->>API: SUBSCRIBE /topic/market/{symbol}/{timeframe}
    API->>Stream: Open reference-counted stream
    Stream->>Binance: Subscribe realtime kline stream
    Binance-->>Stream: Closed normalized Candle
    Stream->>DB: Insert if absent
    Stream-->>UI: CANDLE_CLOSED
    Binance--xStream: Disconnect
    Stream->>Stream: Mark DEGRADED + bounded backoff
    Stream->>Binance: Reconnect
    Stream->>DB: Read last persisted open time
    Stream->>Binance: Load historical gap including boundary candle
    Stream->>DB: Deduplicate and persist missing candles
    Stream-->>UI: Recovered candle updates
```

The stream listener is generation-scoped, so callbacks from a replaced Binance
connection are ignored. Recovery deliberately includes the last persisted
boundary candle; the database key and store contract make its repeat harmless.

## M3 — Strategy plugin discovery and execution boundary

```mermaid
sequenceDiagram
    participant Spring as Spring bootstrap
    participant Factories as StrategyFactory beans
    participant Registry as StrategyRegistry
    participant API as StrategyController
    participant Consumer as Future backtester
    participant Strategy as Strategy plugin

    Spring->>Factories: Discover independent beans
    Spring->>Registry: Construct with discovered factories
    Registry->>Registry: Validate unique type + version
    API->>Registry: availableStrategies()
    Registry-->>API: Versioned schemas/defaults
    Consumer->>Registry: create(StrategyDefinition)
    Registry->>Factories: Select by type + version
    Factories-->>Consumer: Strategy abstraction
    Consumer->>Strategy: analyze(normalized StrategyContext)
    Strategy-->>Consumer: Signal
```

The consumer and API depend on registry/strategy abstractions only. A new plugin
adds one implementation and factory bean; Backtester, Evaluator, Ranking,
controllers, and the JSONB-based schema remain unchanged.

## M4 — Synchronous single-candidate experiment

```mermaid
sequenceDiagram
    actor Client
    participant API as ExperimentController
    participant Pipeline as ExperimentPipelineService
    participant DB as PostgreSQL repository
    participant Backtest as BacktestPort
    participant Strategy as StrategyRegistry/plugins
    participant Eval as ExperimentEvaluator
    participant Rank as Ranking

    Client->>API: POST /api/v1/experiments + candidate + candles
    API->>Pipeline: execute(immutable plan)
    Pipeline->>DB: persist candidate, dataset, snapshots, CREATED
    Pipeline->>DB: CREATED → RUNNING
    Pipeline->>Backtest: run(candidateId, datasetRef, executionConfig)
    loop candle prefix N
        Backtest->>Strategy: analyze(candles[0..N])
        Strategy-->>Backtest: signal after candle N close
        Backtest->>Backtest: fill pending signal at N+1 open
    end
    Backtest-->>Pipeline: signals + trades + equity curve
    Pipeline->>Eval: evaluate(backtest result)
    Eval-->>Pipeline: versioned metrics + score
    Pipeline->>DB: atomic artifacts + metrics + COMPLETED
    Pipeline->>Rank: rank(completed evaluations)
    Pipeline->>DB: replace leaderboard projection
    Pipeline-->>API: completed experiment details
    API-->>Client: 201 + reproducible result
```

M4 deliberately runs one supplied candidate in-process. Candidate generation,
durable job dispatch, RabbitMQ, worker claiming, and event publication belong to
M5 and are not implied by this sequence.

## M5.1 — Bounded Random candidate generation

```mermaid
sequenceDiagram
    actor Client
    participant API as SearchRunController
    participant Local as Bounded local executor
    participant Search as SearchCoordinator
    participant Generator as StrategyGenerator
    participant DB as PostgreSQL search repository

    Client->>API: POST /api/v1/search-runs
    API->>Search: create(exact config + seed + stops)
    Search->>DB: persist CREATED SearchRun
    API->>Local: schedule generation
    API-->>Client: 202 + searchRunId + Location
    Local->>Search: run(command)
    Search->>DB: CREATED → RUNNING
    Search->>Generator: generate(context)
    loop one bounded batch
        Generator-->>Search: next lazy CandidateStrategy
        Search->>Search: check count/time/no-improvement
        Search->>DB: persist batch + progress
        Search->>DB: read cancel/feedback state
    end
    Search->>DB: RUNNING → COMPLETED or CANCELLED
    Client->>API: GET status or POST cancel
    API->>DB: read progress / atomically cancel run and non-started work
```

The local executor remains the bounded generation mechanism and does not execute
backtests. M5.2 extends its persistence boundary with durable dispatch; M5.3
executes confirmed jobs only in the independent worker application.

## M5.2 — Transactional backtest-job dispatch

```mermaid
sequenceDiagram
    participant Search as SearchCoordinator
    participant DB as PostgreSQL
    participant Relay as Outbox publisher
    participant Queue as RabbitMQ

    Search->>DB: transaction(candidate + CREATED experiment + PENDING_DISPATCH job + outbox)
    Relay->>DB: claim unpublished rows with lease
    Relay->>Queue: persistent BacktestJob
    alt broker ACK and routable
        Queue-->>Relay: publisher confirm ACK
        Relay->>DB: transaction(outbox published + job QUEUED + experiment QUEUED)
    else NACK, timeout, or returned
        Queue-->>Relay: failure
        Relay->>DB: attempts + error + next retry; keep PENDING_DISPATCH
    end
```

Publisher confirm establishes the reporting boundary. Merely persisting or
sending a job never makes it `QUEUED`. A crash after broker ACK but before the
confirmation transaction can produce a duplicate publish on retry; later worker
idempotency uses `experimentId` as required by the specification.

## M5.3 — Idempotent Backtest Worker

```mermaid
sequenceDiagram
    participant Queue as RabbitMQ
    participant Worker as Backtest Worker
    participant DB as PostgreSQL / Outbox
    participant Relay as Domain-event relay

    Queue-->>Worker: durable BacktestJob (at-least-once)
    Worker->>DB: atomic claim(experimentId, workerId, lease)
    alt claimed
        Worker->>Worker: deterministic backtest + evaluate
        Worker->>DB: transaction(signals + trades + metrics + COMPLETED + BacktestCompleted outbox)
        DB-->>Worker: commit
        Worker-->>Queue: manual ACK
        Relay->>Queue: persistent BacktestCompleted after commit
    else completed or cancelled duplicate
        Worker-->>Queue: ACK without recomputing
    else active unexpired claim
        Worker-->>Queue: NACK and requeue until completion or lease expiry
    else transient failure and retries remain
        Worker->>DB: transaction(RETRY_PENDING + delayed retry outbox)
        Worker-->>Queue: ACK current delivery
    else invalid or retries exhausted
        Worker->>DB: transaction(FAILED + error)
        Worker-->>Queue: reject without requeue → DLQ
    end
```

The claim update is safe across worker replicas and an expired lease can be
reclaimed after a crash. A delivery that races ahead of the dispatch-confirm
transaction is requeued rather than incorrectly acknowledged. Retry count and
last error are durable job state; the retry outbox prevents the current message
from being ACKed before the replacement delivery intent is committed.

## M5.4/M5.5 — async ranking and realtime updates

This sequence is implemented with transactional outboxes, consumer-specific
inbox identities, and separate search/leaderboard STOMP topics.

```mermaid
sequenceDiagram
    actor User
    participant UI as Dashboard
    participant API as API Application
    participant Search as SearchCoordinator
    participant Generator as StrategyGenerator
    participant DB as PostgreSQL / Outbox
    participant Queue as RabbitMQ
    participant Worker as Backtest Worker
    participant Eval as Evaluator
    participant Rank as Ranking

    User->>UI: START SEARCH
    UI->>API: POST /api/v1/search-runs
    API->>Search: start(command)
    Search->>Generator: generate(context)
    Generator-->>Search: CandidateStrategy stream
    Search->>DB: persist candidate, experiment, job intent
    DB-->>Queue: publish durable job after commit
    Queue-->>Worker: BacktestJob (at-least-once)
    Worker->>DB: atomic claim
    Worker->>Worker: deterministic backtest
    Worker->>DB: trades + metrics + COMPLETED + outbox
    DB-->>Queue: BacktestCompleted
    Queue-->>Eval: consume idempotently
    Eval->>DB: evaluation + StrategyEvaluated outbox
    Queue-->>Rank: StrategyEvaluated
    Rank->>DB: idempotent leaderboard projection
    Rank-->>API: LeaderboardUpdated
    API-->>UI: WebSocket update
```

## M5.5 — cancellation and replica scaling

```mermaid
sequenceDiagram
    actor User
    participant API as API Application
    participant DB as PostgreSQL / Outbox
    participant Queue as RabbitMQ
    participant W1 as Worker replica 1
    participant W3 as Worker replicas 2–3

    User->>API: POST /search-runs/{id}/cancel
    API->>DB: transaction(CANCELLED + cancel non-started jobs/experiments + tombstone outbox)
    alt message was already published
        Queue-->>W1: BacktestJob
        W1->>DB: conditional claim sees CANCELLED
        W1-->>Queue: ACK without artifacts
    else job is already RUNNING
        W1->>DB: complete safely and commit
    end
    Note over W1,W3: replica count changes from 1 to 3; same image and queue
    Queue-->>W1: shared workload
    Queue-->>W3: shared workload
    W1->>DB: atomic claim by experimentId
    W3->>DB: atomic claim by experimentId
```

## Boundaries and latency path

- Synchronous: browser → API validation; worker → local backtest engine.
- Asynchronous: job dispatch, completion, evaluation, ranking, and UI updates.
- Primary latency path: candidate persistence → broker → worker → evaluator →
  ranking → WebSocket.
- Backpressure: generation must stream/batch and cap in-flight jobs.
- Ordering is local to an ordering key; consumers never assume global order.

## M7 — Dashboard search and Top #1 provenance

```mermaid
sequenceDiagram
    actor User
    participant UI as Dashboard
    participant Market as Market REST/STOMP
    participant API as Search/Experiment API
    participant DB as PostgreSQL
    participant Queue as RabbitMQ
    participant Workers as Worker replicas 1..3

    User->>UI: Select timeframe, plugins, policy, generator, stops
    UI->>Market: GET backend candle snapshot
    Market-->>UI: Normalized candles
    UI->>API: POST /datasets with exact snapshot
    API->>DB: Idempotent immutable dataset + checksum
    API-->>UI: MarketDatasetRef
    UI->>API: POST /search-runs?generator=random|genetic
    API->>DB: Persist exact search provenance
    API-->>UI: searchRunId + progress topic
    DB-->>Queue: Confirmed durable jobs through outbox
    Queue-->>Workers: At-least-once delivery
    Workers->>DB: Atomic claim and one completion per experimentId
    DB-->>UI: Progress/leaderboard WebSocket events
    UI->>API: GET leaderboard
    API-->>UI: Rank #1 linked by experimentId
    UI->>API: GET details + GET provenance
    API-->>UI: Signals, trades, metrics, immutable reproduction snapshot
```

Changing the Market timeframe only replaces its REST request and STOMP
subscription. Strategy catalog, search, leaderboard, News, and status modules
retain their state and do not reload.

## M6 — isolated News and Sentiment collection

```mermaid
sequenceDiagram
    participant Schedule as Dedicated news executor
    participant Collector as NewsCollector
    participant Provider as NewsProvider adapter
    participant DB as PostgreSQL NewsStore
    participant Model as SentimentAnalyzer adapter
    participant API as News REST/health

    Schedule->>Collector: collect()
    Collector->>Provider: fetch(since)
    alt provider available
        Provider-->>Collector: normalized NewsItem list
        Collector->>DB: upsert news first
        loop prediction not already stored for exact versions
            Collector->>Model: analyze(NewsItem)
            alt inference succeeds
                Model-->>Collector: versioned SentimentResult
                Collector->>DB: insert idempotently
            else bounded attempts fail
                Collector->>Collector: mark Sentiment DEGRADED/DOWN + metric
            end
        end
    else provider unavailable
        Collector->>Collector: mark News DOWN + metric; do not throw to caller
    end
    API->>DB: read already stored news and sentiment
```

Provider failure does not invoke or alter Market Data, Search, Backtest,
Evaluation, or Leaderboard components. Model failure occurs after the normalized
news upsert, so headlines remain available and failed inference can be retried on
the next collection without fabricating metadata.

## Failure points

- Broker unavailable: persisted dispatch intent stays pending; API must not call
  it durably queued before publisher confirmation.
- Worker crash: unacknowledged job is redelivered; atomic claim/idempotency
  prevents duplicate completion.
- Commit/event gap: transactional outbox retries event publication.
- Duplicate event: consumer inbox prevents duplicate projection updates.
- Cancel: generation stops; queued work is skipped safely where practical.
- News provider/model failure: local health degrades and stored news remains
  readable; unrelated schedulers and application paths continue independently.
