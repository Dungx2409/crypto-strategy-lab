# FEATURE_SPEC.md — Crypto Strategy Lab (Java)

> **Purpose:** This file is the implementation contract for a coding agent.  
> The agent MUST treat this document as the source of truth for the Java implementation unless a newer specification explicitly overrides it.

---

## 0. Requirement language

The keywords **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

Requirement labels:

- **[SOURCE]** — directly grounded in the supplied *Crypto Strategy Lab / Software Architecture* seminar material.
- **[DECISION]** — an implementation decision added here so a coding agent can build a complete, executable Java system.
- **[OPTIONAL]** — useful extension, but not required for the MVP.

The seminar explicitly emphasizes that the goal is **software architecture**, not finding the most profitable trading strategy. The implementation MUST therefore prioritize modifiability, replaceability, scalability, reliability, observability, and reproducibility over financial sophistication.

---

# 1. Product summary

## 1.1 System name

**Crypto Strategy Lab**

## 1.2 Product goal

Build an experimental platform that:

1. receives realtime cryptocurrency market data from Binance;
2. normalizes exchange-specific data into a stable internal model;
3. supports multiple pluggable trading strategies;
4. combines strategy signals using interchangeable combination policies;
5. generates many candidate strategy configurations;
6. backtests candidates;
7. evaluates and ranks results on a leaderboard;
8. continuously searches for better candidates with explicit stop conditions;
9. collects crypto news and produces sentiment results through a replaceable sentiment component;
10. streams realtime market/search/leaderboard updates to a dashboard;
11. records enough provenance that any top result can be traced to the exact strategy versions, parameters, dataset, execution configuration, model version, and code version that created it;
12. proves the architecture by surviving change, scale, and failure scenarios.

## 1.3 Non-goals

The MVP MUST NOT:

- place real buy/sell orders;
- manage wallets or private keys;
- promise profitable trading results;
- optimize for high-frequency trading;
- require Kubernetes;
- require a service mesh;
- require Event Sourcing;
- require CQRS if a simpler read model is sufficient;
- require Kafka merely because event-driven architecture is used;
- make a specific ML model part of Strategy domain logic.

---

# 2. Required demo story

The final system MUST be able to demonstrate this story end-to-end:

1. User opens the dashboard.
2. User selects `BTCUSDT`.
3. User selects one of:
   - `5m`
   - `15m`
   - `1h`
   - `4h`
4. Dashboard displays market candles and receives realtime updates.
5. User selects one or more baseline strategies:
   - Moving Average (`MA`)
   - Relative Strength Index (`RSI`)
   - Bollinger Bands (`BB`)
   - Support/Resistance (`SR`)
6. User clicks **START SEARCH**.
7. System generates candidate strategies.
8. Candidates are submitted as backtest jobs.
9. Workers backtest candidates.
10. Evaluator calculates metrics.
11. Ranking updates the leaderboard.
12. Dashboard shows the number of candidates tested.
13. User clicks the current Top #1 result.
14. System displays:
    - candidate definition;
    - strategy versions;
    - strategy parameters;
    - trades;
    - signals;
    - total return;
    - maximum drawdown;
    - total trades;
    - dataset/timeframe;
    - execution configuration;
    - timestamps;
    - code version;
    - model version if sentiment was used.
15. Dashboard displays News + Sentiment.
16. A `SentimentStrategy` can be added without changing Backtester/Evaluator core logic.
17. Search can be run again.
18. The group then performs architecture proof scenarios described in Section 18.

---

# 3. Architectural drivers

The implementation MUST be designed around these eight quality attributes.

| Driver | Required behavior |
|---|---|
| Modifiability | Adding a new strategy such as MACD must not require edits to Backtester, Evaluator, Leaderboard, or frontend core logic. |
| Scalability | Architecture must support growth from ~100 candidates toward 100,000 candidates without converting core logic into a different architecture. |
| Performance | Backtests must be executable in parallel through independent workers. |
| Realtime | New market candles must be propagated to the dashboard through a realtime channel. |
| Reliability | Binance disconnect must reconnect and recover gaps without duplicate candles; News failure must not stop market/backtest flows. |
| Maintainability | Search algorithm must be replaceable without rewriting Backtester. |
| Observability | The system must expose loop status, queue depth, job latency, failure counts, reconnect counts, and health state. |
| Reproducibility | A leaderboard result must link back to the exact experiment configuration and versions that produced it. |

### 3.1 Measurable local-development targets [DECISION]

These are implementation targets, not claims from the seminar:

- New closed candle → dashboard update: **p95 <= 2 seconds** on a normal local development environment, excluding exchange/network outages.
- Duplicate persisted candles for the same `(symbol, timeframe, openTime)`: **0**.
- Duplicate completed experiment for the same `experimentId`: **0**.
- Adding a new Strategy implementation: no changes to Backtester, Evaluator, Ranking, or REST controllers.
- Switching `RandomStrategyGenerator` → `GeneticStrategyGenerator`: no changes to Backtester, Evaluator, or Ranking.
- News provider unavailable: `/market`, market WebSocket, search, backtest, evaluation, and leaderboard remain operational.
- One worker → three workers: no core code modification; only configuration/replica count changes.

---

# 4. Overall architecture

## 4.1 Architecture style

**[DECISION] Start as a modular monolith plus independently runnable backtest workers.**

Reason:

- the seminar explicitly accepts Modular Monolith as a valid architecture;
- bounded contexts and contracts matter more than the number of deployables;
- backtest workers have a different scaling profile and need independent replication;
- this keeps the student project understandable while still proving worker scaling.

The repository MUST allow the API application and worker application to be run as separate processes from the same codebase.

## 4.2 Bounded contexts

The code MUST be divided into these business boundaries.

### A. Market Data

Owns:

- `Candle`
- `TradingPair`
- `Timeframe`
- exchange provider abstraction
- Binance adapter
- historical candle retrieval
- realtime candle stream
- reconnect
- gap recovery
- candle persistence/deduplication

### B. Strategy

Owns:

- `Strategy`
- `StrategyDefinition`
- `Signal`
- strategy parameters
- strategy registry
- composite strategies
- `CombinationPolicy`
- `StrategyGenerator`
- candidate generation

### C. Experiment

Owns:

- `SearchRun`
- `CandidateStrategy`
- `BacktestJob`
- `Experiment`
- `Trade`
- `BacktestResult`
- `Evaluation`
- `Ranking`
- leaderboard projection
- stop conditions
- provenance

### D. News Intelligence

Owns:

- `NewsItem`
- `NewsProvider`
- `NewsCollector`
- `SentimentAnalyzer`
- `SentimentResult`
- model metadata
- `SentimentStrategy` integration boundary

### E. API / Presentation

Owns:

- REST controllers;
- WebSocket gateway;
- DTO mapping;
- validation;
- dashboard static assets;
- presentation-only models.

API code MUST NOT contain indicator calculations, backtest calculations, candidate generation logic, or exchange-specific JSON parsing.

---

# 5. Java technology baseline

## 5.1 Required

**[DECISION]**

- Java **21 LTS**
- Spring Boot **3.x**
- Maven
- Spring Web
- Spring WebSocket
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Flyway database migrations
- RabbitMQ for durable backtest jobs and domain-event delivery
- Spring AMQP
- Spring Boot Actuator
- Micrometer
- Jackson
- JUnit 5
- AssertJ
- Mockito
- Testcontainers for PostgreSQL and RabbitMQ integration tests
- ArchUnit for dependency/architecture tests
- Docker
- Docker Compose

## 5.2 Financial numeric types

The system MUST use `BigDecimal` for:

- OHLC prices;
- volume where practical;
- fees;
- capital;
- P&L;
- return;
- drawdown;
- scores where decimal precision matters.

The system MUST NOT use `float` for financial calculations.

`double` MAY be used internally for mathematical indicator calculations where a library requires it, but values persisted as business results MUST be converted deliberately and tested.

## 5.3 Time

- All backend timestamps MUST use UTC.
- Java domain/application code SHOULD use `Instant`.
- Exchange candle `openTime` MUST be stored as UTC.
- Frontend MAY render timestamps in local browser time.

---

# 6. Repository layout

The coding agent SHOULD create this layout:

```text
crypto-strategy-lab/
├── pom.xml
├── README.md
├── FEATURE_SPEC.md
├── docker-compose.yml
├── .env.example
├── docs/
│   ├── architecture/
│   │   ├── C4_CONTEXT.md
│   │   ├── C4_CONTAINER.md
│   │   ├── DYNAMIC_VIEW.md
│   │   └── EVENT_CATALOG.md
│   └── adr/
│       ├── ADR-001-market-data-adapter.md
│       ├── ADR-002-websocket-realtime.md
│       ├── ADR-003-strategy-plugin-registry.md
│       ├── ADR-004-backtester-evaluator-separation.md
│       ├── ADR-005-queue-worker.md
│       ├── ADR-006-modular-monolith.md
│       ├── ADR-007-provenance.md
│       ├── ADR-008-news-sentiment-separation.md
│       ├── ADR-009-cqrs-event-sourcing.md
│       └── ADR-010-loop-stop-observability.md
├── api-app/
│   └── src/main/java/com/cryptolab/...
├── worker-app/
│   └── src/main/java/com/cryptolab/...
├── core/
│   └── src/main/java/com/cryptolab/...
└── integration-tests/
```

If a simpler single-Maven-module repository is chosen, the same boundaries MUST still exist as packages and MUST be protected by ArchUnit rules.

Recommended package-by-feature structure inside `core`:

```text
com.cryptolab
├── marketdata
│   ├── domain
│   ├── application
│   ├── port
│   └── adapter
├── strategy
│   ├── domain
│   ├── application
│   ├── port
│   └── adapter
├── experiment
│   ├── domain
│   ├── application
│   ├── port
│   └── adapter
├── news
│   ├── domain
│   ├── application
│   ├── port
│   └── adapter
└── shared
```

---

# 7. Dependency rules

## 7.1 Clean Architecture rule

Business policy MUST depend on stable interfaces/ports, not infrastructure implementations.

Forbidden example:

```java
public final class RsiStrategy {
    private final BinanceHttpClient binance;
    private final JdbcTemplate jdbc;
}
```

Required direction:

```java
public interface Strategy {
    Signal analyze(StrategyContext context);
}
```

```java
public interface MarketDataPort {
    List<Candle> candles(
        TradingPair pair,
        Timeframe timeframe,
        Instant from,
        Instant to
    );
}
```

`RsiStrategy` MAY receive a `StrategyContext` containing normalized candles. It MUST NOT know:

- Binance JSON format;
- PostgreSQL schema;
- WebSocket protocol;
- RabbitMQ;
- REST controllers.

## 7.2 ArchUnit rules

At minimum, tests MUST assert:

1. `..domain..` does not depend on `..adapter..`.
2. `..domain..` does not depend on Spring MVC/WebSocket classes.
3. Strategy implementations do not depend on Backtester implementation classes.
4. Experiment domain does not depend on Binance adapter classes.
5. News domain does not depend on Strategy infrastructure classes.
6. Controllers depend on application services, not repositories directly.

---

# 8. Market Data feature

## 8.1 Normalized Candle contract

Use a stable exchange-neutral model:

```java
public record Candle(
    String symbol,
    Timeframe timeframe,
    Instant openTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}
```

Minimum supported timeframes:

```java
public enum Timeframe {
    M5("5m"),
    M15("15m"),
    H1("1h"),
    H4("4h");

    private final String exchangeCode;
}
```

## 8.2 Provider port

```java
public interface MarketDataProvider {

    List<Candle> loadHistorical(
        TradingPair pair,
        Timeframe timeframe,
        Instant from,
        Instant to
    );

    MarketSubscription subscribe(
        TradingPair pair,
        Timeframe timeframe,
        CandleListener listener
    );
}
```

## 8.3 Binance adapter

Baseline provider MUST be Binance.

Responsibilities:

- translate Binance REST payload → `Candle`;
- translate Binance WebSocket payload → `Candle`;
- contain every Binance-specific URL, field name, DTO, and protocol detail;
- never expose Binance DTOs outside adapter package.

## 8.4 Provider replaceability

The code MUST allow an `OkxMarketDataAdapter` or another provider to implement the same `MarketDataProvider` contract.

Frontend MUST NOT change because the exchange provider changes.

## 8.5 Reconnect and gap recovery

When Binance WebSocket disconnects:

1. adapter detects disconnect;
2. health becomes `DEGRADED`;
3. reconnect begins with bounded exponential backoff;
4. after reconnect, system reads the last persisted candle open time;
5. historical REST retrieval fills the missing interval;
6. recovered candles pass through the same normalization path;
7. database unique constraint removes duplicates;
8. realtime stream resumes;
9. metrics record reconnect and recovered gaps.

Required uniqueness key:

```text
(symbol, timeframe, open_time)
```

The system MUST be safe if the final candle before disconnect is received again.

## 8.6 Market REST API

### Get candles

```http
GET /api/v1/market/candles?symbol=BTCUSDT&timeframe=5m&limit=500
```

Response:

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "candles": [
    {
      "openTime": "2026-08-18T01:00:00Z",
      "open": "115000.10",
      "high": "115200.00",
      "low": "114950.20",
      "close": "115180.40",
      "volume": "123.45"
    }
  ]
}
```

Validation:

- unsupported symbol → `400`;
- unsupported timeframe → `400`;
- limit > configured max → `400`;
- provider unavailable with no cached data → `503`;
- provider unavailable with cached data MAY return cached data plus degraded status metadata.

## 8.7 Market WebSocket topic

```text
/topic/market/{symbol}/{timeframe}
```

Example payload:

```json
{
  "type": "CANDLE_CLOSED",
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "openTime": "2026-08-18T01:00:00Z",
  "open": "115000.10",
  "high": "115200.00",
  "low": "114950.20",
  "close": "115180.40",
  "volume": "123.45"
}
```

---

# 9. Strategy feature

## 9.1 Core contract

```java
public interface Strategy {

    StrategyDescriptor descriptor();

    Signal analyze(StrategyContext context);
}
```

```java
public record StrategyDescriptor(
    String type,
    String version,
    Map<String, Object> parameters
) {}
```

```java
public enum SignalType {
    BUY,
    SELL,
    HOLD
}
```

```java
public record Signal(
    SignalType type,
    BigDecimal strength,
    Instant at,
    String reason
) {}
```

`strength` SHOULD be normalized to `[-1, 1]`.

## 9.2 Baseline strategies

The MVP MUST provide exactly these baseline strategy families:

1. `MovingAverageStrategy`
2. `RsiStrategy`
3. `BollingerBandsStrategy`
4. `SupportResistanceStrategy`

### 9.2.1 Moving Average [DECISION]

Default candidate parameter examples:

- fast/slow = `10/20`
- `20/50`
- `50/200`

Rules SHOULD be deterministic.

Example:

- fast MA crosses above slow MA → BUY;
- fast MA crosses below slow MA → SELL;
- otherwise HOLD.

### 9.2.2 RSI [DECISION]

Candidate examples:

- period `14`, oversold `30`, overbought `70`;
- period `14`, oversold `20`, overbought `80`.

Example:

- RSI <= oversold → BUY;
- RSI >= overbought → SELL;
- otherwise HOLD.

### 9.2.3 Bollinger Bands [DECISION]

Parameters MUST be configurable:

- window;
- deviation multiplier.

Example:

- close < lower band → BUY;
- close > upper band → SELL;
- otherwise HOLD.

### 9.2.4 Support/Resistance [DECISION]

Parameters MUST be configurable and deterministic.

A simple MVP implementation MAY derive local support/resistance from a configurable rolling window.

## 9.3 Strategy registry

No `switch(strategyType)` should be scattered across the application.

Required abstraction:

```java
public interface StrategyRegistry {
    void register(StrategyFactory factory);
    Strategy create(StrategyDefinition definition);
    Set<String> registeredTypes();
}
```

Recommended Spring implementation:

- each `StrategyFactory` is a bean;
- registry discovers factories once;
- registering a new strategy only adds a new factory/implementation.

## 9.4 MACD architecture proof

Baseline production registration SHOULD NOT include MACD before the extensibility demonstration.

The codebase MUST make this change possible:

```java
public final class MacdStrategy implements Strategy {
    // implementation
}
```

and one registry registration / bean addition.

Adding MACD MUST NOT require modifying:

- Backtester;
- Evaluator;
- Ranking;
- Leaderboard controller;
- market provider;
- database schema for strategy-specific columns;
- frontend core.

Strategy parameters MUST therefore be stored as flexible versioned configuration, e.g. JSON/JSONB snapshots.

---

# 10. Composite Strategy

Individual strategy signal production MUST be separated from signal combination.

## 10.1 Combination policy

```java
public interface CombinationPolicy {

    CombinedSignal combine(List<WeightedSignal> signals);
}
```

Required implementations:

1. `MajorityVotePolicy`
2. `WeightedVotePolicy`

## 10.2 Majority vote

Mapping:

- BUY = `+1`
- HOLD = `0`
- SELL = `-1`

If sum > 0 → BUY.  
If sum < 0 → SELL.  
If sum == 0 → HOLD.

## 10.3 Weighted vote

Example:

```text
MA  weight = 0.2, signal = +1
RSI weight = 0.3, signal = -1
SR  weight = 0.5, signal = +1

score = 1*0.2 + (-1)*0.3 + 1*0.5 = 0.4
```

Default [DECISION]:

- score > `0.10` → BUY
- score < `-0.10` → SELL
- otherwise HOLD

Threshold MUST be configurable and saved as provenance.

---

# 11. Candidate Strategy and Search

## 11.1 Candidate model

```java
public record CandidateStrategy(
    UUID candidateId,
    List<StrategyDefinition> strategies,
    CombinationPolicyDefinition combinationPolicy,
    String candidateHash
) {}
```

`candidateHash` MUST be deterministic from canonicalized candidate configuration.

Two candidates with exactly the same definitions/versions/parameters/policy MUST produce the same hash.

## 11.2 Strategy generator contract

```java
public interface StrategyGenerator {

    Stream<CandidateStrategy> generate(SearchContext context);
}
```

Backtester, Evaluator, and Ranking MUST depend only on candidate contracts, not the concrete generator.

## 11.3 Required generators

### `RandomStrategyGenerator`

MUST exist as the baseline generator.

It MUST accept a persisted `randomSeed`.

Given the same:

- search config;
- registered strategy versions;
- parameter space;
- random seed;

it MUST generate the same candidate sequence.

### `GeneticStrategyGenerator`

**[DECISION]** Implement as a second interchangeable generator for the replaceability proof.

A minimal genetic algorithm is acceptable:

1. initialize population;
2. evaluate population;
3. select parents;
4. crossover;
5. mutate;
6. create next generation;
7. stop according to `StopCondition`.

The algorithm does not need to discover profitable strategies. It needs to prove replaceability and architecture.

## 11.4 Generator selection

Configuration:

```yaml
crypto:
  search:
    generator: random
```

Allowed values:

- `random`
- `genetic`

Changing this value MUST NOT require changes to Backtester, Evaluator, or Leaderboard code.

---

# 12. Continuous Strategy Loop

Required conceptual loop:

```text
Generate → Backtest → Evaluate → Rank → Leaderboard → Generate next
```

The loop MUST NOT be an unbounded `while (true)`.

## 12.1 Stop conditions

The API MUST support at least:

```java
public record StopConditions(
    Long maxCandidates,
    Duration maxDuration,
    Integer noImprovementIterations
) {}
```

User cancel MUST always be supported independently.

At least one automatic stop condition MUST be configured for each search run.

## 12.2 Search run states

```java
public enum SearchRunStatus {
    CREATED,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}
```

`PAUSED` MAY be omitted from MVP if pause/resume is not implemented, but the queue architecture SHOULD not prevent adding it later.

---

# 13. Backtest pipeline

## 13.1 Pipeline

```text
StrategyGenerator
    ↓
CandidateStrategy
    ↓
BacktestJob Queue
    ↓
BacktestWorker(s)
    ↓
BacktestResult
    ↓
Evaluator
    ↓
StrategyEvaluated event
    ↓
Ranking
    ↓
LeaderboardUpdated event
    ↓
Frontend
```

## 13.2 Backtest port

```java
public interface BacktestPort {
    BacktestResult run(BacktestCommand command);
}
```

SearchCoordinator MUST call a port / submit a job. It MUST NOT instantiate a concrete backtest engine.

## 13.3 Backtest command

```java
public record BacktestCommand(
    UUID experimentId,
    UUID candidateId,
    MarketDatasetRef dataset,
    ExecutionConfig executionConfig
) {}
```

## 13.4 Execution configuration

Must be persisted as part of provenance.

```java
public record ExecutionConfig(
    BigDecimal initialCapital,
    BigDecimal feeRate,
    boolean allowShort,
    String fillPolicy,
    String engineVersion
) {}
```

MVP defaults [DECISION]:

- initial capital = `10000`
- fee rate = configurable, default `0.001`
- `allowShort = false`
- fill policy = `NEXT_CANDLE_OPEN`
- engine version = application-defined semantic version

### Look-ahead protection [DECISION]

A signal calculated from candle `N` MUST NOT fill at a price that was unknowable before candle `N` completed.

Default rule:

- calculate signal after candle N closes;
- execute on candle N+1 open.

This rule MUST be covered by tests.

## 13.5 Trades

```java
public record Trade(
    Instant entryTime,
    BigDecimal entryPrice,
    Instant exitTime,
    BigDecimal exitPrice,
    BigDecimal quantity,
    BigDecimal fee,
    BigDecimal pnl
) {}
```

For MVP:

- BUY opens a long position if none exists;
- SELL closes the open long position;
- repeated BUY while already long does not open another position unless explicitly configured;
- no leverage;
- no shorting by default.

## 13.6 Required metrics

At minimum:

```java
public record EvaluationMetrics(
    BigDecimal totalReturnPct,
    BigDecimal maxDrawdownPct,
    int totalTrades,
    BigDecimal score
) {}
```

Optional metrics:

- win rate;
- Sharpe ratio;
- profit factor.

These optional metrics MUST NOT be required by core ranking contracts.

## 13.7 Evaluation/ranking rule [DECISION]

The exact scoring formula is not defined by the seminar.

MVP default MUST be deterministic and configurable.

Recommended default:

```text
score = totalReturnPct - (0.5 * abs(maxDrawdownPct))
```

Tie-break:

1. higher score;
2. higher total return;
3. lower absolute max drawdown;
4. lower `experimentId` lexical order for deterministic display.

The formula/version MUST be persisted in provenance.

---

# 14. Queue and worker semantics

## 14.1 Broker

Use RabbitMQ.

Queue names:

```text
crypto.backtest.jobs
crypto.backtest.jobs.dlq
crypto.domain.events
```

The exact exchange topology MAY be refined, but backtest job delivery MUST be durable.

## 14.2 Worker requirements

A worker MUST:

- be stateless between jobs;
- be safe to run as 1 or many replicas;
- use the database for durable experiment state;
- be idempotent;
- acknowledge a queue message only after durable completion state is committed;
- reject/route poison messages to DLQ after bounded retries.

## 14.3 Idempotency

`experimentId` is the primary job idempotency key.

On receiving a job:

1. if experiment is `COMPLETED`, worker MUST acknowledge and do nothing;
2. if experiment is already `RUNNING`, duplicate handling MUST be safe;
3. worker must use atomic compare/update or locking so two workers cannot complete the same experiment independently;
4. trades and metrics MUST have database constraints preventing duplicate completion artifacts.

## 14.4 Retry

[DECISION]

- transient infrastructure failures: max 3 retries;
- exponential backoff;
- invalid candidate/config: no retry;
- exhausted retry → `FAILED` + DLQ.

Retry count and last error MUST be observable.

---

# 15. Transaction boundary and Outbox

The seminar requires the design to reason about this failure:

```text
save trades
save metrics
mark COMPLETED
publish BacktestCompleted
```

A crash between database commit and event publish must not leave the system permanently inconsistent.

## 15.1 Required implementation [DECISION]

Use the **Transactional Outbox pattern**.

In one database transaction:

1. persist trades;
2. persist metrics;
3. update experiment status to `COMPLETED`;
4. create `outbox_event` row for `BacktestCompleted`.

After commit:

- OutboxPublisher publishes the event to RabbitMQ.
- When confirmed, mark outbox row as published.
- If publisher crashes, unpublished rows are retried.

This makes database result + event intent atomic.

## 15.2 Consumer idempotency

Consumers of domain events MUST record processed `eventId` in an inbox/deduplication table or equivalent idempotent projection mechanism.

A duplicated RabbitMQ delivery MUST NOT duplicate leaderboard entries.

---

# 16. Event-driven contracts

The project event catalog MUST include these nine event types:

1. `MarketPriceUpdated`
2. `CandleClosed`
3. `StrategyGenerated`
4. `BacktestStarted`
5. `BacktestCompleted`
6. `StrategyEvaluated`
7. `LeaderboardUpdated`
8. `NewsCollected`
9. `SentimentAnalyzed`

## 16.1 Common event envelope

```java
public record DomainEventEnvelope<T>(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    String aggregateType,
    UUID aggregateId,
    String correlationId,
    String causationId,
    String orderingKey,
    T payload
) {}
```

Every event MUST define:

- owner;
- schema version;
- ordering key;
- duplicate handling;
- consumer failure behavior;
- replay policy.

## 16.2 Event ordering

[DECISION]

Recommended ordering keys:

- market events: `symbol + ":" + timeframe`;
- experiment events: `experimentId`;
- search/leaderboard events: `searchRunId`;
- news/sentiment events: `newsId`.

Consumers MUST NOT assume global ordering.

## 16.3 Event ownership

| Event | Owner |
|---|---|
| MarketPriceUpdated | Market Data |
| CandleClosed | Market Data |
| StrategyGenerated | Strategy/Search |
| BacktestStarted | Experiment |
| BacktestCompleted | Experiment |
| StrategyEvaluated | Experiment/Evaluation |
| LeaderboardUpdated | Experiment/Ranking |
| NewsCollected | News Intelligence |
| SentimentAnalyzed | News Intelligence |

---

# 17. Experiment provenance and reproducibility

Every completed experiment MUST answer:

- Which candidate?
- Which strategy types?
- Which strategy versions?
- Which exact parameters?
- Which combination policy and weights?
- Which dataset?
- Which timeframe?
- Which symbol?
- Which dataset date range?
- Which dataset checksum/version?
- Which fee?
- Which initial capital?
- Which fill policy?
- Which evaluator/scoring version?
- Which search generator?
- Which search configuration?
- Which random seed?
- Which code commit/build version?
- Which sentiment model/version if used?
- Which preprocessing/input version if used?
- When did it start/end?
- What was its final status?
- What metrics were produced?

## 17.1 Dataset reference

```java
public record MarketDatasetRef(
    String symbol,
    Timeframe timeframe,
    Instant from,
    Instant to,
    String datasetVersion,
    String checksum
) {}
```

`checksum` SHOULD be calculated from a canonical ordered representation of candles.

## 17.2 Code version

Read from environment:

```text
APP_GIT_COMMIT
APP_BUILD_VERSION
```

If unavailable in local dev, use:

```text
APP_GIT_COMMIT=dev
```

but production/demo builds SHOULD inject the real Git commit.

## 17.3 Reproducibility endpoint

```http
GET /api/v1/experiments/{experimentId}/provenance
```

The response MUST contain enough information to create a new equivalent backtest.

## 17.4 Rerun endpoint

[DECISION]

```http
POST /api/v1/experiments/{experimentId}/rerun
```

The new experiment MUST reference the source experiment as:

```text
reproductionOfExperimentId
```

The system SHOULD compare old/new metrics and report whether the rerun matches within configured numeric tolerance.

---

# 18. Architecture proof acceptance tests

These are first-class product requirements.

## 18.1 Proof A — Extensibility

Scenario:

> Add MACD.

Pass criteria:

- create `MacdStrategy` + its factory/registration;
- no modification to:
  - Backtester;
  - Evaluator;
  - Ranking;
  - Leaderboard API;
  - Market Data;
- no new strategy-specific database column;
- tests pass.

An ArchUnit/integration test MUST document this invariant.

## 18.2 Proof B — Replaceability

Scenario:

> Change `RandomStrategyGenerator` to `GeneticStrategyGenerator`.

Pass criteria:

- change configuration or dependency binding;
- Backtester code unchanged;
- Evaluator code unchanged;
- Leaderboard code unchanged;
- both generators produce valid `CandidateStrategy` objects.

## 18.3 Proof C — Worker scaling

Scenario:

> Worker replicas `1 → 3`.

Pass criteria:

- no core code modification;
- same queue;
- no duplicate completed experiments;
- throughput increases or queue backlog drains faster under the same synthetic workload;
- metrics show worker count, processed jobs, queue depth, failure count.

The seminar uses 100 → 100,000 candidates as the architectural scale scenario. The implementation MUST be able to enqueue a 100,000-candidate search without keeping all 100,000 heavy backtest results in memory at once.

For a classroom demo, a smaller processed sample MAY be used if the architecture and queue behavior are still observable.

## 18.4 Proof D — News failure isolation

Scenario:

> News provider/service is DOWN.

Pass criteria:

- realtime market chart works;
- Market Data health remains independent;
- Strategy analysis works;
- Backtest works;
- leaderboard works;
- News panel shows degraded/unavailable state;
- errors are logged and counted;
- no cascading system failure.

## 18.5 Proof E — Binance disconnect recovery

Scenario:

> Binance WebSocket disconnects.

Pass criteria:

- market component becomes degraded;
- reconnect happens automatically;
- missing candles are fetched;
- no duplicated persisted candle;
- frontend resumes updates without full application restart;
- reconnect/gap metrics are visible.

## 18.6 Proof F — Provenance

Scenario:

> Open Top #1 six months later.

Pass criteria:

Top result shows exact:

- candidate spec;
- strategy versions;
- parameters;
- dataset/timeframe/version/checksum;
- execution config;
- search generator/config/seed;
- score formula version;
- code version;
- sentiment model metadata if applicable.

---

# 19. Leaderboard

## 19.1 Read model

Leaderboard MAY be a dedicated projection table even without adopting full CQRS.

Required view:

```text
Rank | Strategy | Return | MDD | Trades | Score
```

Each row MUST link to `experimentId`.

## 19.2 Endpoint

```http
GET /api/v1/leaderboard?searchRunId={id}&limit=50
```

Example:

```json
{
  "searchRunId": "2b0...",
  "items": [
    {
      "rank": 1,
      "experimentId": "b31...",
      "strategySummary": "MA+RSI+SR",
      "returnPct": "18.2",
      "maxDrawdownPct": "-6.1",
      "totalTrades": 81,
      "score": "15.15"
    }
  ]
}
```

## 19.3 Leaderboard update event

When a newly evaluated experiment changes Top-K:

- persist/update projection idempotently;
- publish `LeaderboardUpdated`;
- push to frontend WebSocket topic.

Topic:

```text
/topic/leaderboard/{searchRunId}
```

---

# 20. Search REST API

## 20.1 List available strategies

```http
GET /api/v1/strategies
```

Response MUST expose:

- type;
- version;
- parameter schema/defaults.

## 20.2 Start search

```http
POST /api/v1/search-runs
Content-Type: application/json
```

Example:

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "dataset": {
    "from": "2026-07-01T00:00:00Z",
    "to": "2026-08-01T00:00:00Z"
  },
  "strategyTypes": ["MA", "RSI", "BB", "SR"],
  "combinationPolicy": {
    "type": "WEIGHTED",
    "weights": {
      "MA": 0.2,
      "RSI": 0.3,
      "SR": 0.5
    }
  },
  "generator": "random",
  "randomSeed": 2409,
  "stopConditions": {
    "maxCandidates": 125,
    "maxDurationSeconds": 600,
    "noImprovementIterations": 50
  },
  "execution": {
    "initialCapital": "10000",
    "feeRate": "0.001",
    "allowShort": false,
    "fillPolicy": "NEXT_CANDLE_OPEN"
  }
}
```

Response:

```json
{
  "searchRunId": "uuid",
  "status": "RUNNING"
}
```

## 20.3 Search status

```http
GET /api/v1/search-runs/{id}
```

Must include:

- status;
- generatedCandidates;
- queuedJobs;
- runningJobs;
- completedJobs;
- failedJobs;
- bestScore;
- start time;
- elapsed time;
- active stop conditions.

## 20.4 Cancel search

```http
POST /api/v1/search-runs/{id}/cancel
```

Cancellation MUST:

- stop generating new candidates;
- prevent queued but not started jobs from being considered required work where practical;
- allow in-flight jobs to finish or safely stop according to implementation;
- mark search run `CANCELLED`.

---

# 21. News + Sentiment

## 21.1 Separation

Required flow:

```text
NewsProvider
    ↓
NewsCollector
    ↓
Normalized NewsItem
    ↓
SentimentAnalyzer
    ↓
SentimentResult
    ↓
SentimentStrategy (optional consumer)
```

Changing news provider MUST NOT require changing sentiment model.

Changing sentiment model MUST NOT require changing news collector.

Strategy Engine MUST NOT know whether sentiment came from BERT, FinBERT, LLM, rules, or a remote service.

## 21.2 News model

```java
public record NewsItem(
    String newsId,
    String provider,
    String title,
    String url,
    Instant publishedAt,
    String normalizedText,
    String inputVersion
) {}
```

## 21.3 Sentiment port

```java
public interface SentimentAnalyzer {
    SentimentResult analyze(NewsItem item);
}
```

```java
public record SentimentResult(
    String newsId,
    SentimentLabel sentiment,
    BigDecimal score,
    ModelDescriptor model,
    String inputVersion,
    String preprocessingVersion,
    Instant createdAt
) {}
```

```java
public record ModelDescriptor(
    String name,
    String version
) {}
```

```java
public enum SentimentLabel {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}
```

## 21.4 Default implementation [DECISION]

To keep the Java project fully runnable without an expensive ML dependency:

- provide `DeterministicKeywordSentimentAnalyzer` as the default local/demo adapter;
- identify it with a real version, e.g. `keyword-v1`;
- provide a stable `SentimentAnalyzer` port;
- optionally add a `RemoteSentimentModelAdapter` later.

Do NOT fake the metadata by claiming BERT/FinBERT was used when it was not.

## 21.5 Sentiment strategy

`SentimentStrategy` MUST consume normalized `SentimentResult` objects.

It MUST NOT directly invoke the model adapter.

---

# 22. Frontend / Dashboard

The seminar recommends a single SPA dashboard for an MVP.

**[DECISION]** To keep the project Java-centric, the default frontend MAY be a lightweight HTML/CSS/JavaScript SPA served from Spring Boot static resources.

A React/Vue SPA MAY replace it later without changing domain modules.

## 22.1 Required panels

1. **Market panel**
   - symbol selector;
   - timeframe selector;
   - candlestick/price chart;
   - realtime connection status.

2. **Strategy panel**
   - available strategies;
   - parameter editing;
   - combination policy;
   - generator selection.

3. **Search panel**
   - START SEARCH;
   - cancel;
   - candidates tested;
   - queue/running/completed/failed counts;
   - elapsed time.

4. **Leaderboard panel**
   - rank;
   - strategy;
   - return;
   - MDD;
   - trades;
   - score.

5. **Experiment details**
   - trades;
   - signals;
   - metrics;
   - full provenance.

6. **News + Sentiment panel**
   - headline;
   - published time;
   - sentiment;
   - score;
   - model version.

7. **System status**
   - Market Data: UP/DEGRADED/DOWN;
   - News: UP/DEGRADED/DOWN;
   - queue/worker status.

## 22.2 Timeframe change isolation

Changing the chart timeframe from `5m` to `1h` MUST:

- unsubscribe from the old chart stream;
- load the selected chart data;
- subscribe to the new stream;
- NOT reload the whole backend;
- NOT restart the search subsystem;
- NOT reload unrelated News/Leaderboard state unless needed.

---

# 23. Persistence model

Use PostgreSQL.

The exact schema MAY evolve, but migrations MUST produce equivalent concepts.

## 23.1 `candles`

```text
symbol             varchar
timeframe          varchar
open_time          timestamptz
open               numeric
high               numeric
low                numeric
close              numeric
volume             numeric
provider           varchar
received_at        timestamptz

PRIMARY KEY (symbol, timeframe, open_time)
```

## 23.2 `search_runs`

```text
id                          uuid PK
status                      varchar
symbol                      varchar
timeframe                   varchar
generator_type              varchar
generator_version           varchar
random_seed                 bigint
search_config_json          jsonb
stop_conditions_json        jsonb
execution_config_json       jsonb
created_at                  timestamptz
started_at                  timestamptz
ended_at                    timestamptz
cancel_requested            boolean
```

## 23.3 `candidates`

```text
id                  uuid PK
search_run_id       uuid FK
candidate_hash      varchar
candidate_spec_json jsonb
created_at          timestamptz

UNIQUE (search_run_id, candidate_hash)
```

## 23.4 `experiments`

```text
id                          uuid PK
candidate_id                uuid FK
search_run_id               uuid FK
status                      varchar
dataset_ref_json            jsonb
execution_config_json       jsonb
strategy_snapshot_json      jsonb
combination_policy_json     jsonb
generator_snapshot_json     jsonb
evaluator_version           varchar
code_commit                 varchar
build_version               varchar
reproduction_of_id          uuid NULL
started_at                  timestamptz
completed_at                timestamptz
failure_code                varchar NULL
failure_message             text NULL
version                     bigint
```

`version` SHOULD be used for optimistic locking.

## 23.5 `trades`

```text
id              uuid PK
experiment_id   uuid FK
sequence_no     int
entry_time      timestamptz
entry_price     numeric
exit_time       timestamptz
exit_price      numeric
quantity        numeric
fee             numeric
pnl             numeric

UNIQUE (experiment_id, sequence_no)
```

## 23.6 `evaluation_metrics`

```text
experiment_id       uuid PK/FK
total_return_pct    numeric
max_drawdown_pct    numeric
total_trades        int
score               numeric
metrics_json        jsonb
```

## 23.7 `leaderboard_entries`

```text
search_run_id       uuid
experiment_id       uuid
rank                int
score               numeric
return_pct          numeric
max_drawdown_pct    numeric
total_trades        int
updated_at          timestamptz

PRIMARY KEY (search_run_id, experiment_id)
```

## 23.8 `news_items`

```text
news_id             varchar PK
provider            varchar
title               text
url                 text
published_at        timestamptz
normalized_text     text
input_version       varchar
created_at          timestamptz
```

## 23.9 `sentiment_predictions`

```text
id                      uuid PK
news_id                 varchar FK
sentiment               varchar
score                   numeric
model_name              varchar
model_version           varchar
input_version           varchar
preprocessing_version   varchar
created_at              timestamptz
```

## 23.10 `outbox_events`

```text
event_id            uuid PK
aggregate_type      varchar
aggregate_id        uuid
event_type          varchar
schema_version      int
payload_json        jsonb
created_at          timestamptz
published_at        timestamptz NULL
attempt_count       int
last_error          text NULL
```

## 23.11 `processed_events`

```text
consumer_name       varchar
event_id            uuid
processed_at        timestamptz

PRIMARY KEY (consumer_name, event_id)
```

---

# 24. Observability

## 24.1 Health

Expose:

```text
/actuator/health
/actuator/metrics
```

Health components SHOULD include:

- PostgreSQL;
- RabbitMQ;
- Binance realtime connection;
- News provider;
- Sentiment analyzer.

Market and News health MUST be separately visible.

## 24.2 Required metrics

At minimum:

```text
crypto_search_runs_active
crypto_candidates_generated_total
crypto_backtest_queue_depth
crypto_backtest_jobs_started_total
crypto_backtest_jobs_completed_total
crypto_backtest_jobs_failed_total
crypto_backtest_job_duration_seconds
crypto_backtest_duplicate_delivery_total
crypto_market_ws_reconnect_total
crypto_market_gap_candles_recovered_total
crypto_market_candle_to_ui_latency_seconds
crypto_news_collection_failures_total
crypto_sentiment_inference_failures_total
crypto_sentiment_inference_duration_seconds
crypto_outbox_pending
```

Naming MAY follow Micrometer conventions.

## 24.3 Structured logging

Every async path MUST propagate identifiers where applicable:

- `correlationId`;
- `searchRunId`;
- `candidateId`;
- `experimentId`;
- `eventId`;
- `jobId`.

Do not log secrets or API keys.

---

# 25. Failure handling

## 25.1 Binance unavailable

- Market status → DEGRADED/DOWN.
- Use stored candles where appropriate.
- Reconnect automatically.
- Gap recovery after reconnect.
- Search using already-materialized historical dataset MAY continue.
- Do not crash unrelated News/Leaderboard APIs.

## 25.2 News provider unavailable

- News status → DEGRADED/DOWN.
- Existing News items remain readable.
- Sentiment on unavailable new items pauses/fails locally.
- Market/search/backtest remain functional.

## 25.3 RabbitMQ unavailable

- API must not falsely report a job as queued if broker delivery is not durable.
- Search status should expose degraded queue state.
- Outbox/event publication should retry after recovery.

## 25.4 Database unavailable

- Return `503` for state-changing operations.
- Do not acknowledge worker jobs whose durable result cannot be committed.

## 25.5 Sentiment model unavailable

- News collection MAY continue.
- Sentiment status becomes degraded.
- no cascading failure into Market/Experiment.
- failed inference is observable and retry policy is bounded.

---

# 26. API error format

Use one consistent error contract:

```json
{
  "timestamp": "2026-08-18T01:00:00Z",
  "status": 400,
  "code": "INVALID_TIMEFRAME",
  "message": "Unsupported timeframe: 2m",
  "correlationId": "..."
}
```

Validation errors MUST be deterministic and testable.

---

# 27. Configuration

Example `application.yml`:

```yaml
crypto:
  market:
    provider: binance
    symbol-default: BTCUSDT
    reconnect:
      initial-delay-ms: 1000
      max-delay-ms: 30000

  search:
    generator: random
    default-max-candidates: 125

  backtest:
    queue: crypto.backtest.jobs
    max-retries: 3

  news:
    provider: mock

  sentiment:
    provider: keyword

  provenance:
    git-commit: ${APP_GIT_COMMIT:dev}
    build-version: ${APP_BUILD_VERSION:dev}
```

Secrets MUST come from environment variables and MUST NOT be committed.

---

# 28. Required automated tests

## 28.1 Unit tests

MUST cover:

- MA signals;
- RSI signals;
- Bollinger signals;
- Support/Resistance signals;
- MajorityVotePolicy;
- WeightedVotePolicy;
- candidate canonical hash;
- deterministic random generator with seed;
- backtest fill timing / no look-ahead;
- fee calculation;
- total return;
- max drawdown;
- stop conditions;
- evaluator score;
- event serialization;
- candle deduplication logic.

## 28.2 Integration tests

Using Testcontainers:

1. PostgreSQL migration test.
2. RabbitMQ job publish/consume test.
3. worker completes experiment transactionally.
4. duplicated job does not create duplicated result.
5. outbox republishes after simulated publisher failure.
6. leaderboard projection is idempotent.
7. search cancellation.
8. provenance can reconstruct an equivalent command.

## 28.3 Architecture tests

Using ArchUnit:

- domain independence rules from Section 7;
- strategy extension isolation;
- controller/repository separation.

## 28.4 Failure tests

MUST include:

- fake Binance disconnect + reconnect + repeated candle;
- fake gap recovery;
- News adapter throwing exception while market API still passes;
- worker crash/retry simulation;
- duplicated domain event delivery.

## 28.5 Scale test

Create a synthetic lightweight dataset and candidate generator able to enqueue a large count.

Test goals:

- generator streams candidates rather than materializing all candidates;
- queue depth is observable;
- multiple workers consume safely;
- completed experiment count equals unique submitted job count;
- no out-of-memory caused solely by holding all candidate results in RAM.

---

# 29. Security and safety

This is a research/education platform, not an execution bot.

MUST:

- use public market data endpoints where possible;
- never store exchange secret keys in source;
- never expose environment secrets through API;
- validate all REST inputs;
- limit maximum request sizes;
- use parameterized persistence/JPA;
- escape/sanitize untrusted news text before rendering;
- set HTTP client timeouts;
- set bounded retry policies;
- restrict CORS in non-development profiles.

Authentication MAY be omitted for a localhost classroom MVP unless separately required.

---

# 30. AI coding-agent implementation rules

The coding agent MUST follow these rules.

1. **Do not create a God Service.**
   - No class may own market data + strategy + backtest + ranking + news + UI responsibilities.

2. **Do not start with infrastructure names.**
   - Implement ports/domain contracts first.

3. **Do not leak provider DTOs.**
   - Binance JSON must stop at the Binance adapter.

4. **Do not put a `switch` for every strategy in multiple layers.**
   - Use registry/factory/plugin design.

5. **Do not let strategies query PostgreSQL directly.**
   - Supply a normalized `StrategyContext`.

6. **Do not let Search know concrete Backtester implementation.**
   - Use `BacktestPort`/job submission contract.

7. **Do not create all 100,000 candidate results in one in-memory list.**
   - Generate/submit in a streaming/batched fashion.

8. **Do not use an unbounded loop.**
   - Stop condition is mandatory.

9. **Do not publish a completion event outside a durability strategy.**
   - Use transactional outbox.

10. **Do not assume exactly-once message delivery.**
    - Design consumers for at-least-once delivery and idempotency.

11. **Do not hide failures.**
    - Health, logs, and metrics must expose them.

12. **Do not make sentiment model identity implicit.**
    - Persist model name/version/input/preprocessing version.

13. **Do not introduce Microservices, Kubernetes, Service Mesh, CQRS, or Event Sourcing just for appearance.**
    - Add only if a documented driver requires them.

14. **Do not claim architecture proof without automated evidence.**
    - Every proof scenario needs a test, script, metric, or reproducible demo.

15. **Every database change requires a Flyway migration.**

16. **Every externally visible contract requires tests.**

17. **Every random process used in reproducible experiments requires a stored seed.**

18. **Every experiment must be immutable in its configuration snapshot after it starts.**

19. **A completed leaderboard record must always link to an experiment.**

20. **No TODO may remain in a MUST-have core flow at final delivery.**

---

# 31. Milestone implementation plan

Follow the seminar's seven-milestone progression.

## M1 — Architecture Skeleton

Implement:

- repository structure;
- bounded-context packages;
- domain models;
- ports/interfaces;
- Spring bootstrap;
- PostgreSQL;
- Flyway;
- ArchUnit boundaries;
- architecture docs.

Exit criteria:

- project builds;
- architecture tests pass;
- C4 Context + Container docs exist;
- no real Binance dependency is needed yet.

## M2 — Walking Skeleton

Implement end-to-end:

```text
Binance → Java backend → normalized Candle → WebSocket → chart
```

Also:

- historical REST load;
- persistence;
- WebSocket reconnect;
- basic health.

Exit criteria:

- selecting BTCUSDT + timeframe displays candles;
- a new candle reaches browser;
- exchange DTO does not leak outside adapter.

## M3 — Strategy Plugin

Implement:

- Strategy contract;
- registry/factory;
- MA;
- RSI;
- BB;
- SR;
- CombinationPolicy;
- unit tests;
- extension architecture test.

Exit criteria:

- four strategies visible via API;
- strategies run on the same normalized context;
- a test-only new strategy can be registered without core edits.

## M4 — Experiment Pipeline

Implement:

```text
Candidate → Backtest → Evaluate → Rank
```

Also:

- Experiment state machine;
- trades;
- metrics;
- provenance;
- leaderboard;
- details endpoint.

Exit criteria:

- one candidate can be backtested;
- metrics saved;
- leaderboard row created;
- clicking result returns complete experiment details.

## M5 — Continuous Loop

Implement:

- RandomStrategyGenerator;
- GeneticStrategyGenerator;
- SearchCoordinator;
- RabbitMQ queue;
- worker app;
- multiple worker safety;
- stop conditions;
- cancel;
- outbox;
- metrics.

Exit criteria:

- start search generates many candidates;
- multiple workers process jobs;
- progress updates live;
- stop conditions terminate generation;
- duplicate delivery test passes.

## M6 — News + Sentiment

Implement:

- NewsProvider;
- collector;
- normalized NewsItem;
- SentimentAnalyzer;
- versioned SentimentResult;
- News + Sentiment dashboard panel;
- optional SentimentStrategy.

Exit criteria:

- news result displayed;
- sentiment model metadata displayed;
- changing sentiment adapter does not change Strategy core;
- News failure does not break Market/Backtest.

## M7 — Architecture Proof

Implement evidence for:

- MACD extension;
- Random → Genetic replacement;
- worker 1 → 3 scaling;
- News failure isolation;
- Binance reconnect/gap recovery;
- Top #1 provenance.

Exit criteria:

- `mvn verify` passes;
- Docker Compose demo starts;
- proof scripts/tests are documented;
- architecture docs match actual code.

---

# 32. Docker Compose

The project SHOULD run with:

```bash
docker compose up --build
```

Recommended services:

```text
postgres
rabbitmq
api
worker-1
```

Additional workers can be started with:

```bash
docker compose up --scale worker=3
```

If Compose service scaling conflicts with fixed container names, do not set `container_name` for worker replicas.

Kubernetes manifests are OPTIONAL.

---

# 33. Developer commands

Expected commands:

```bash
# build + unit/integration/architecture tests
mvn clean verify

# run API locally
mvn -pl api-app spring-boot:run

# run worker locally
mvn -pl worker-app spring-boot:run

# run infrastructure/application
docker compose up --build

# scale workers
docker compose up --scale worker=3
```

The actual module flags may be adapted to the final Maven structure, but README MUST provide exact working commands.

---

# 34. Definition of Done

The project is complete only when all of the following are true.

## Functional

- [ ] Binance historical market data works.
- [ ] Binance realtime WebSocket works.
- [ ] 5m/15m/1h/4h timeframe selection works.
- [ ] MA works.
- [ ] RSI works.
- [ ] Bollinger Bands works.
- [ ] Support/Resistance works.
- [ ] Composite Majority policy works.
- [ ] Composite Weighted policy works.
- [ ] Random candidate generation works.
- [ ] Alternative generator can replace Random.
- [ ] Backtest works.
- [ ] Evaluation produces Return/MDD/Trades/Score.
- [ ] Leaderboard updates.
- [ ] Search has stop conditions.
- [ ] Search can be cancelled.
- [ ] News collection works with at least one adapter.
- [ ] Sentiment result works with a versioned analyzer.
- [ ] Dashboard shows market/search/leaderboard/news.
- [ ] Top result displays complete provenance.

## Architecture

- [ ] Market/Strategy/Experiment/News boundaries exist in code.
- [ ] Domain does not depend on Binance/PostgreSQL/RabbitMQ/Web controllers.
- [ ] New strategy does not require Backtester changes.
- [ ] New provider does not require frontend changes.
- [ ] New generator does not require Backtester/Evaluator changes.
- [ ] Worker count changes without core code change.
- [ ] News failure is isolated.
- [ ] Binance disconnect recovers.
- [ ] Duplicate jobs/events are safe.
- [ ] completion DB write + completion event use a durability strategy.
- [ ] experiment provenance is immutable and queryable.

## Quality

- [ ] `mvn clean verify` passes.
- [ ] Flyway migrations work from an empty database.
- [ ] Docker Compose starts from a clean machine with documented prerequisites.
- [ ] Unit tests cover strategy/backtest/evaluation logic.
- [ ] Integration tests cover DB/queue/outbox.
- [ ] ArchUnit tests cover dependency rules.
- [ ] Failure tests exist.
- [ ] Observability metrics exist.
- [ ] README contains demo instructions.
- [ ] C4 Context/Container/Dynamic docs are consistent with code.
- [ ] ADRs record major decisions and trade-offs.

---

# 35. Required architecture documentation

## 35.1 C4 Context

Must show:

```text
User/Trader
    ↓
Crypto Strategy Lab
   ↙          ↘
Binance      News Providers
```

Do not put PostgreSQL/RabbitMQ/Spring inside the Context view.

## 35.2 C4 Container

Must show at least:

```text
Frontend
   ↓ REST/WS
Backend/API
   ├── Market Data → Exchange Adapter → Binance
   ├── Strategy/Search → Backtest Jobs → Worker(s)
   ├── News → Sentiment
   └── Database
```

## 35.3 Dynamic view

Must document:

```text
User
→ START SEARCH
→ SearchCoordinator
→ StrategyGenerator
→ CandidateStrategy
→ enqueue
→ BacktestWorker
→ BacktestResult
→ Evaluator
→ StrategyEvaluated
→ Ranking
→ LeaderboardUpdated
→ Frontend
```

The view must identify:

- synchronous boundaries;
- asynchronous boundaries;
- main data flow;
- failure points;
- latency path.

---

# 36. ADRs

Create short ADRs using:

```text
Context
Decision
Alternatives
Consequences
Evidence
```

Required topics:

1. Why `MarketDataProvider` + Adapter?
2. Why WebSocket for realtime UI?
3. Why Strategy Plugin/Registry?
4. Why Backtester and Evaluator are separate?
5. Why queue/worker?
6. Why modular monolith + worker process instead of immediate microservices?
7. How experiment provenance is stored?
8. Why News Collector and Sentiment Analyzer are separate?
9. Why CQRS/Event Sourcing are used or deliberately not used?
10. Why stop conditions and loop observability are mandatory?

---

# 37. Explicit decisions about advanced architecture

## 37.1 Microservices

MVP: **NO** for every bounded context.

Only backtest worker is independently runnable/scalable.

Extract more services only if there is evidence for:

- independent scaling;
- fault isolation;
- independent deployment;
- distinct runtime/resource profile.

## 37.2 Kubernetes

MVP: **NOT REQUIRED**.

Docker Compose is enough to prove worker replication.

Kubernetes is optional evidence if the group already has time and infrastructure.

## 37.3 Service Mesh

MVP: **NO**.

## 37.4 CQRS

Full CQRS: **NOT REQUIRED**.

A dedicated leaderboard projection is allowed because its read shape is different, but do not create separate services/databases solely to call the design CQRS.

## 37.5 Event Sourcing

MVP: **NO**.

Keep state + domain events/outbox.

Event Sourcing MAY be added only if audit/replay requirements justify the complexity.

## 37.6 AI Agent strategy generator

OPTIONAL.

If added, it MUST implement the same `StrategyGenerator` contract:

```java
public final class AgentStrategyGenerator implements StrategyGenerator {
    // Observe → Plan → Act → Evaluate → Stop/Improve
}
```

Backtester/Evaluator MUST remain unaware that an AI Agent generated the candidate.

---

# 38. Final acceptance scenario

A clean evaluator should be able to perform this sequence:

```text
docker compose up --build
```

Then:

1. open dashboard;
2. see `BTCUSDT`;
3. choose `5m`;
4. see historical candles;
5. see realtime candle update;
6. choose MA + RSI + BB + SR;
7. choose Random generator;
8. start 125-candidate search;
9. see tested candidate count increase;
10. see leaderboard update;
11. open Top #1;
12. inspect trades + Return + MDD + signals;
13. inspect complete provenance;
14. see News + Sentiment;
15. simulate News failure and verify Market/Search still work;
16. simulate Binance disconnect and verify reconnect + gap recovery;
17. switch Random → Genetic without changing Backtester;
18. scale worker 1 → 3 and compare queue/throughput metrics;
19. add/register MACD and verify architecture isolation;
20. run:

```bash
mvn clean verify
```

and all tests pass.

---

# 39. Final principle for the coding agent

The code is not considered successful merely because it "runs."

It is successful when the implementation can **prove**:

- a strategy can be added cheaply;
- a provider can be replaced behind an adapter;
- a generator can be replaced behind a contract;
- backtests can scale through workers;
- failures are isolated;
- retries do not corrupt state;
- realtime market data can recover;
- results are observable;
- Top-K results are reproducible;
- architecture documentation matches the code.

When choosing between a fashionable technology and a simpler design that satisfies these proofs, choose the simpler design.
