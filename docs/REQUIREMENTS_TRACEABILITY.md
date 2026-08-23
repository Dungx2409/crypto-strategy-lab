# Requirements Traceability

This file tracks delivery evidence without treating planned work as complete.
The detailed normative requirements remain in `FEATURE_SPEC.md`.

| Scope | Specification sections | Delivery milestone | Current status | Required evidence |
|---|---|---|---|---|
| Repository and technology baseline | 4–7, 27, 31–33 | P0/M1 | M1 complete | Maven reactor, Wrapper, Enforcer, Spring bootstraps, Flyway, ArchUnit tests |
| Market Data | 8, 18.5, 25.1 | M2 | M2 complete | Unit, adapter, REST/UI isolation, reconnect/gap failure, and PostgreSQL duplicate tests |
| Strategy and combination | 9–10, 18.1 | M3 | M3 complete | Strategy/policy unit tests, catalog API test, extension proof, and ArchUnit isolation |
| Candidate and experiment pipeline | 11, 13, 17, 19 | M4 | M4 complete | Determinism, no-look-ahead, metrics, provenance, rerun, leaderboard, and PostgreSQL tests |
| Search, messaging, and workers | 11–16, 18.2–18.3, 20 | M5 | M5.5 complete | Random/Genetic replaceability, complete cancellation, transactional outbox/inbox, live progress, worker metrics, and 1→3 scaling proof |
| News and sentiment | 21, 18.4, 25.2, 25.5 | M6 | M6 complete | Adapter replacement, persistence/idempotency, and failure-isolation tests |
| Dashboard and final proof | 22, 24, 34–39 | M7 | M7 complete | Backend-driven dashboard, operational metrics/status, focused proof script, full verification, and Compose startup |

## P0 evidence

| Requirement | Evidence |
|---|---|
| Java 21 | Parent POM compiler release and Maven Enforcer rule |
| Maven reproducibility | Maven Wrapper configuration |
| Multi-module dependency direction | Reactor/module POM dependencies and `core` banned-dependency Enforcer rule |
| Secrets are not committed | `.gitignore` and `.env.example` |
| Incremental implementation order | `docs/IMPLEMENTATION_PLAN.md` |
| Continuous verification | `.github/workflows/verify.yml` |

## M1 evidence

| Requirement | Evidence |
|---|---|
| Bounded contexts and framework-independent core | Core package-by-feature contracts plus Maven Enforcer and ArchUnit |
| Independently runnable API and worker | `CryptoStrategyApiApplication` and `CryptoStrategyWorkerApplication` executable jars |
| PostgreSQL and Flyway from an empty database | `V1__create_initial_schema.sql` and `PostgresqlMigrationIT` Testcontainers test |
| Domain contract invariants | `DomainContractTest` |
| Required architecture views | C4 Context, C4 Container, Dynamic View, and Event Catalog |
| Major architecture decisions | ADR-001 through ADR-010 |

## M2 evidence

| Requirement | Evidence |
|---|---|
| Historical market candles | Selectable `BinanceMarketDataProvider` or `OkxMarketDataProvider`, payload/provider tests, and the unchanged `/api/v1/market/candles` contract |
| Realtime open and closed candle updates | Normalized `CandleUpdate`, Java HTTP WebSocket transport, and STOMP `/topic/market/{symbol}/{timeframe}`; open updates replace the current browser candle while only closed candles persist |
| Provider DTO isolation | Package-private Binance and OKX candle DTOs plus a market-adapter ArchUnit no-leak rule |
| Reconnect and gap recovery | `MarketDataStreamServiceTest` fake disconnect/reconnect/repeated-candle scenario |
| Duplicate protection | PostgreSQL primary key, `ON CONFLICT DO NOTHING`, and `CandleStoreIT` |
| Four independent timeframes | Four browser chart states, independent STOMP subscription IDs, reference-counted backend streams, 1m/5m/15m/1h/4h support, `ReferenceDashboardTest`, and `MarketDashboardIsolationTest` |
| Basic observability | Market health indicator and reconnect/recovery/UI-latency Micrometer meters |
| M3 remained outside M2 | M2 verification predates the MA/RSI/BB/SR and combination implementations delivered in M3 |

## M3 evidence

| Requirement | Evidence |
|---|---|
| Four baseline families | `MovingAverageStrategy`, `RsiStrategy`, `BollingerBandsStrategy`, `SupportResistanceStrategy` and signal unit tests |
| Shared normalized context | Framework-independent `StrategyContext` validates one pair/timeframe and strict candle ordering |
| Versioned plugin creation | Four `StrategyFactory` beans plus `SpringStrategyRegistry` type/version uniqueness and validation tests |
| Strategy catalog | `GET /api/v1/strategies` returns four plugins with parameter schemas/defaults |
| Majority and weighted policies | Deterministic policy implementations and boundary tests; weighted threshold is configurable |
| Extensibility isolation | Production `MacdStrategy`/factory registration plus ArchUnit rules forbidding API/Experiment/Worker dependencies on concrete baseline or MACD implementations |
| Flexible persistence | Existing JSONB candidate/strategy/policy snapshot columns; no M3 migration or strategy-specific columns |
| M4 remained outside the M3 delivery | M3 verification predates the experiment behavior delivered separately in M4 |

## M4 evidence

| Requirement | Evidence |
|---|---|
| Candidate → Backtest → Evaluate → Rank | `ExperimentPipelineService` and its operation-order unit test |
| No look-ahead bias | `DeterministicBacktestEngine` exposes only candle prefixes and fills a candle-N signal at candle-N+1 open; price/time assertions cover the rule |
| Deterministic identity | Canonical SHA-256 candidate hash and ordered candle dataset checksum tests |
| Required metrics and tie-break | Versioned evaluator plus ranking tests for return, win rate, maximum drawdown, trade count, score, and lexical experiment ID; Flyway V9 stores win rate in evaluation and leaderboard projections |
| Durable artifacts | `JdbcExperimentRepository` transactionally persists signals, trades, metrics, completion state, and leaderboard projection |
| Immutable provenance | PostgreSQL integration test reconstructs candidate, dataset candles/checksum, execution/generator/evaluator/code snapshots and verifies stored JSON remains unchanged |
| Reproduction | Provenance/detail/rerun REST tests and PostgreSQL rerun test compare metrics within the configured tolerance and record `reproductionOfExperimentId` |
| Architecture separation | ArchUnit rules keep Experiment controllers out of Infrastructure and Evaluator/Ranking independent of the concrete backtest engine |
| M5 remains outside M4 | No candidate generator/search loop, RabbitMQ topology, job publisher, worker consumer, outbox, or inbox behavior was added |

## M5.1 evidence

| Requirement | Evidence |
|---|---|
| Deterministic Random generation | `RandomStrategyGeneratorTest` proves equal seed/version/parameter-space produces the same ordered candidate sequence, varies non-empty strategy membership subsets, and changes only ordering for another seed |
| Exact reproducible search input | `SearchContext` persists dataset reference, selected strategy versions, immutable parameter choices, policy, seed, stops, and batch size |
| Streaming rather than materialization | Lazy deterministic permutation spliterator plus coordinator test showing an infinite source is requested only up to `maxCandidates` |
| Bounded batch persistence | `SearchCoordinatorTest` verifies batch sizes; `SearchRunIT` verifies PostgreSQL counts and configuration snapshots |
| Stop conditions | `StopConditionEvaluator` covers candidate count, duration, and no-improvement feedback; finite parameter-space exhaustion is an explicit reason |
| Cancellation | API cancel endpoint plus concurrent PostgreSQL integration test proving cancellation is observed at the next batch boundary |
| Search lifecycle | Generation hands `RUNNING` to nonterminal `EVALUATING`; the last durable terminal worker transaction changes the run to `COMPLETED`; cancellation accepts `EVALUATING`; state-machine, PostgreSQL, and RabbitMQ tests cover handoff and races |
| Schema evolution | Flyway `V2__add_search_run_progress.sql` adds counters, evaluation feedback, terminal reason, and failure fields |
| Architecture isolation | Search controller cannot depend on Infrastructure; generators cannot depend on Backtester, Evaluator, or Ranking |
| M5.1 checkpoint boundary | The M5.1 evidence predates M5.2; at that checkpoint no messaging behavior was claimed |

## M5.2 evidence

| Requirement | Evidence |
|---|---|
| Atomic dispatch intent | `JdbcSearchRunRepository` persists candidate, `Experiment(CREATED)`, `backtest_jobs(PENDING_DISPATCH)`, and dispatch `outbox_events` in one transaction |
| Durable RabbitMQ topology | Durable `crypto.backtest.jobs`, durable `crypto.backtest.jobs.dlq`, direct exchanges, persistent messages, and explicit bindings |
| Confirm before QUEUED | `JdbcBacktestJobOutboxRepository.markConfirmed` atomically marks outbox published, job `QUEUED`, and experiment `QUEUED` only after correlated publisher ACK |
| Broker failure honesty | `BacktestJobOutboxIT` proves an unroutable publish increments retry state but leaves job pending, experiment created, and `queuedJobs=0` |
| Retry and concurrent relay safety | Exponential retry metadata plus PostgreSQL lease claim using `FOR UPDATE SKIP LOCKED` |
| Schema evolution | Flyway `V3__create_backtest_job_dispatch_outbox.sql` adds durable job state and outbox relay claim/routing metadata |
| Architecture boundary | ArchUnit keeps AMQP/RabbitMQ out of `core` and proves the API does not consume backtest jobs |
| M5.2 checkpoint boundary | Worker execution was deliberately absent at this checkpoint and is delivered separately in M5.3 |

## M5.3 evidence

| Requirement | Evidence |
|---|---|
| Manual acknowledgment and commit-before-ACK | `backtestManualAckContainerFactory` uses `MANUAL`; `RabbitBacktestJobListenerTest` asserts ACK occurs only after the synchronous transactional processor returns |
| Atomic claim and crash recovery | `JdbcBacktestWorkerRepository` uses one conditional PostgreSQL update for `QUEUED` or expired `RUNNING` jobs; `BacktestWorkerIT` proves one concurrent owner and lease reclaim |
| Idempotency by `experimentId` | Worker ACKs completed duplicates and requeues active claims until completion/lease expiry; database artifact uniqueness plus deterministic artifact/event IDs protect completion; duplicate Rabbit delivery integration test proves one execution/result/event |
| Atomic completion outbox | One transaction writes signals, trades, metrics, `COMPLETED`, and a `BacktestCompleted` outbox envelope; its relay publishes only committed rows with broker confirms |
| Bounded retry | `BacktestWorkerServiceTest` proves three exponentially delayed retries and then `FAILED`/DLQ outcome; retry intent and state are written transactionally before the current delivery is ACKed |
| Poison handling and DLQ | Invalid JSON/identity and invalid candidate/config are rejected without requeue; RabbitMQ integration test receives malformed input from `crypto.backtest.jobs.dlq` |
| Observable execution state | Flyway `V4__add_backtest_worker_execution.sql` adds retry count, attempts, worker, lease, timestamps, errors, checks, and expired-lease index |
| Architecture boundary | Core exposes framework-neutral worker ports/outcomes and remains free of Spring AMQP/RabbitMQ under Enforcer and ArchUnit |
| Checkpoint boundary | This section records the M5.3 worker evidence; downstream event orchestration was delivered in M5.4 and M5.5 evidence follows below |

## M5.4 evidence

| Requirement | Evidence |
|---|---|
| Transactional completion event | Worker completion persists artifacts, experiment state, and `BacktestCompleted` outbox in one transaction |
| Asynchronous evaluation and ranking | Dedicated RabbitMQ consumers process `BacktestCompleted` then `StrategyEvaluated` without coupling the worker to ranking |
| Inbox deduplication | `processed_events` uniquely identifies consumer/event; duplicate event tests prove one evaluation and one leaderboard projection |
| Idempotent leaderboard | Projection is keyed by experiment and ranking is recalculated deterministically; duplicate delivery does not duplicate rows |

## M5.5 evidence

| Requirement | Evidence |
|---|---|
| Genetic generator | `GeneticStrategyGenerator` implements deterministic initialization, structural evaluation, selection, crossover, mutation, and generation rollover; unit tests prove laziness, repeatability, and valid candidates |
| Generator replaceability | `crypto.search.generator=random|genetic` selects one bean; ArchUnit prevents Backtester, Evaluator, Ranking, and worker orchestration from depending on either concrete generator |
| Complete cancellation | One JDBC transaction marks the run terminal, cancels non-started jobs/experiments, and tombstones unpublished outbox rows; race and broker-delivery integration tests prove cancelled work does not produce artifacts |
| Search progress WebSocket | `SearchProgressPublisher` emits created/running/batch/terminal snapshots to `/topic/search/{searchRunId}` without making durable search depend on WebSocket availability |
| Leaderboard WebSocket | `LeaderboardUpdated` is consumed idempotently and published to `/topic/leaderboard/{searchRunId}`; duplicate listener test proves one UI push |
| Worker observability | Micrometer exposes per-worker active/process/failure meters and shared queue depth; telemetry unit test verifies tags and counts |
| Worker 1→3 proof | Compose scales the portless `worker` service using replica count only; `BacktestWorkerIT` compares one consumer with three independent consumers on the same queue, proves concurrent/faster drain, and asserts one completed experiment/artifact/event per unique ID despite duplicate delivery |

## M6 evidence

| Requirement | Evidence |
|---|---|
| Replaceable collection and analysis | `NewsCollector` depends only on `NewsProvider`, `SentimentAnalyzer`, `NewsStore`, and `NewsTelemetry`; adapter-selection configuration is outside `core` |
| Normalized provider boundary | `CryptoCompareNewsProvider` keeps provider JSON/DTO handling inside its adapter and emits only `NewsItem` |
| Honest, versioned sentiment | `DeterministicKeywordSentimentAnalyzer` records its real model family/version plus preprocessing and input versions; score/model invariants have unit tests and database checks |
| Durable, idempotent results | `JdbcNewsStore` upserts normalized news and uniquely stores predictions by news/model/input/preprocessing identity; `NewsSentimentIT` proves repeat collection creates no duplicate prediction |
| Independent failure behavior | `NewsCollectorTest` proves provider/model exceptions are contained; `NewsFailureIsolationTest` proves a DOWN news provider leaves the market API and market health operational; stored news remains readable in `NewsSentimentIT` |
| Independent observability | Dedicated News and Sentiment health indicators plus failure counters and inference timer; telemetry tests verify failure meters |
| Dashboard contract | `/api/v1/news`, `/api/v1/news/collect`, and the independent News + Sentiment panel expose headline, time, label, score, model version, and degraded state |
| Schema evolution | Flyway `V7__harden_sentiment_prediction_identity.sql` adds sentiment bounds and the exact versioned prediction uniqueness constraint |

## M7 evidence

| Requirement | Evidence |
|---|---|
| Complete backend-driven dashboard | Separate light-theme Realtime, Strategy, Discovery, Backtest, News, and Settings views; four independent charts with candles, volume, MA20, and signals; result metrics, trade rows, entry/exit markers, leaderboard, provenance, and sentiment counts; static scripts call only REST/STOMP contracts; `ReferenceDashboardTest` and `M7DashboardTest` reject missing screens and fabricated data |
| Reproducible search input | `POST /api/v1/datasets` materializes the exact displayed candle snapshot with deterministic checksum and idempotent persistence before SearchRun creation; unit/controller/PostgreSQL tests cover the path |
| Generator runtime replacement | Both Random and Genetic implementations are registered behind `StrategyGenerator`; `?generator=` selects per run and the selected type is persisted; replacement and architecture tests prove downstream independence |
| Operational visibility | `GET /api/v1/system/status`, independent Actuator health components, and Micrometer meters cover active searches, candidates, queue depth, job outcomes/duration/duplicates, outbox backlog, Market recovery/UI latency, and News/Sentiment failure/duration |
| Async traceability | Search, outbox publishers, worker job/event consumers, realtime listeners, and News scheduler log applicable correlation/searchRun/job/experiment/event identifiers |
| Database failure contract | Global `DataAccessException` handling returns consistent `503 Service Unavailable` for state-changing API persistence failures |
| MACD change proof | M7 adds production `MacdStrategy` and `MacdStrategyFactory` with no consumer/controller/Flyway edits; unit/factory tests cover deterministic behavior and validation, `StrategyExtensionArchitectureTest` proves registration, and `ArchitectureRulesTest` protects downstream independence |
| Random → Genetic proof | `GeneratorReplacementArchitectureTest` and `SearchCoordinatorTest` execute equivalent generator output through the same contracts with no downstream changes |
| Worker 1 → 3 proof | `BacktestWorkerIT` and `WorkerScalingArchitectureTest` use the same worker code and queue, show concurrent/faster draining, and assert no duplicate completed experiment artifacts |
| News isolation proof | `NewsFailureIsolationTest` and core failure tests prove provider/model failure does not enter Market, Search, Backtest, or Leaderboard paths |
| Binance recovery proof | `MarketDataStreamServiceTest` and `CandleStoreIT` prove reconnect, boundary-inclusive gap recovery, stale-listener rejection, and duplicate safety |
| Top #1 provenance proof | `ExperimentPipelineIT` follows the top leaderboard `experimentId` to candidate/strategy/policy, dataset/checksum, execution/generator/evaluator, code/build, signals, trades, and metrics |
| Repeatable final evidence | `scripts/verify-architecture-proofs.sh`, `mvn clean verify`, and `docker compose up --build`; mapping is summarized in `docs/architecture/PROOF_MATRIX.md` |

## Post-MVP extension evidence

| Extension | Evidence |
|---|---|
| 30m, 2h, and 1d timeframes | Shared `Timeframe` enum, domain contract test, dashboard controls, and the unchanged provider-neutral REST/STOMP flow |
| Long and Short trading | Versioned deterministic engine, explicit `TradeDirection`, Flyway V10 persistence, REST and dashboard direction output, short P/L unit test, and full verification |
| Position sizing | Engine version 3, immutable `positionSizePct`, partial-capital portfolio accounting, backend capability discovery, dashboard control, deterministic unit test, and full verification |
| Stop Loss and Take Profit | Engine version 4, conservative OHLC ordering, gap policy, persisted exit reason through Flyway V11, dashboard controls, deterministic tests, and full verification |
| Trailing Stop | Engine version 5, completed-candle water marks, conservative gap fills, `TRAILING_STOP` persistence through Flyway V12, dashboard control, deterministic test, and full verification |
| Multiple coins | Configurable application allow-list, BTC/ETH/SOL/BNB dashboard choices, shared provider-neutral market flow, and live Binance API evidence for each added pair |
| Multiple exchanges | Startup-selectable Binance or OKX adapters behind `MarketDataProvider`, provider selection and mapping tests, DTO isolation rule, and live normalized OKX candles through the unchanged REST contract |
| News Sentiment strategy | Versioned sentiment observations in dataset checksum and Flyway V13 persistence, time-filtered `StrategyContext`, `NEWS_SENTIMENT@1.0` plugin factory, no-look-ahead tests, and full verification |
| Evaluator-driven Genetic Search | Genetic version 2, candidate fitness port, durable population barriers, score-based parent selection, deterministic tie breaks, unit and architecture tests, full verification, and two-generation runtime evidence |

## Newly added requirement status

| Requirement | Status |
|---|---|
| Registration, login, and account identity | Complete: server-side sessions, BCrypt, account identity, and ownership guard |
| TradingView-like history and changing last candle | Realtime open-candle behavior complete; visual comparison pending |
| 1,000 users with four realtime charts | Architecture supports shared, reference-counted streams; load test and measurements pending |
| Natural-language or article-link strategy authoring | Prompt path complete: Gemini idea confirmation, restricted JSON, three validation attempts, deterministic smoke test, account-owned versions, list, detail, and deletion. Article-link input remains pending |
| Manual backtest controls and result report | Core execution and metrics exist; account-facing manual workflow and filters are incomplete |
| Continuous non-exhaustive discovery and 24-hour leaderboard | Complete: persisted account schedules repeatedly launch bounded Genetic Search, recover after restart, prevent overlap, and support stop/start controls |
| LLM-assisted crawler selector repair | Not started |
| Current non-OpenAI analysis model bonus | Gemini 2.5 Flash is wired for strategy authoring; API key is intentionally blank by default |
