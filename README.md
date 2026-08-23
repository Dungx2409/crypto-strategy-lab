# Crypto Strategy Lab

Crypto Strategy Lab is a Java 21 research platform for experimenting with
reproducible cryptocurrency trading strategies. It is a university Software
Architecture project; it does not place real orders or promise profitable
results.

The normative product and architecture contract is
[`FEATURE_SPEC.md`](FEATURE_SPEC.md). The approved delivery order is recorded
in [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md).

## Current status

M7 is implemented and the written MVP gaps found during the final audit are
closed. The light browser application now has separate Realtime, Strategy,
Discovery, Backtest, News, and Settings views. Four charts own independent
timeframes and STOMP subscriptions. They render candles, volume, MA20, current
signals, and open-candle updates. The Backtest view renders required metrics,
trade rows, and entry or exit markers. The browser still uses only backend
contracts and materializes the exact primary chart snapshot as an immutable
checksummed dataset before search.

Random and Genetic generators are both registered behind `StrategyGenerator` and
can be selected per search request. Both vary strategy membership as well as
parameters, so one run can compare `MA+RSI`, `BB+SR`, and larger composites. The
worker image remains replica-safe and
scales from one to three instances without a source-code or image change. News
collection remains isolated behind its own ports, executor, health components,
timeouts, and metrics. Transactional outbox/inbox boundaries keep queued state,
experiment completion, evaluation, ranking, and WebSocket projections durable
and idempotent.

The four M3 baseline strategy families remain unchanged. M7 adds production
`MACD@1.0` as a fifth, independently discovered plugin with fast, slow, and
signal EMA periods. The backtester, evaluator, ranking, controllers, and schema
remain strategy-agnostic; the registry endpoint and dashboard discover MACD
through the same factory contract as every other strategy.

The post-MVP extension pass adds 30m, 2h, and 1d market intervals. Engine
version 2 adds Long and Short positions through the same next-candle-open fill
rule. Engine version 3 adds percentage Position Sizing. Engine version 4 adds
Stop Loss and Take Profit with a conservative Stop-first OHLC rule. Every
stored trade records its direction and exit reason. Engine version 5 adds a
Trailing Stop based only on completed-candle water marks. Versions 1 through 4
remain runnable for old experiment provenance.

The default market allow-list includes BTCUSDT, ETHUSDT, SOLUSDT, and BNBUSDT.
Override it with `CRYPTO_MARKET_SUPPORTED_SYMBOLS` without changing market
services or the Binance adapter.

`NEWS_SENTIMENT@1.0` can join the same search space as technical strategies.
Dataset materialization copies versioned sentiment observations into the
experiment dataset and checksum. Backtests expose only observations published
by each candle close, so the strategy cannot read live or future news.

Genetic Search version 2 selects parents from durable evaluator scores. It
persists and evaluates one population before evolving the next. The generator
depends only on a candidate-fitness port; JDBC, RabbitMQ, and worker details
remain outside `core`.

Signed-in users can ask Gemini for a strategy idea, confirm it, and save the
result as restricted JSON. Gemini cannot generate or run Java code. The JSON
can only name registered strategy plugins, versions, parameters, and a
combination policy. The application validates and smoke-tests the result before
storing an account-owned version. Set `GEMINI_API_KEY` in the git-ignored
`.env` file to enable these calls. The checked-in default is blank.

Account-owned discovery schedules repeatedly launch bounded Genetic Search.
Each schedule stores its pair, timeframe, lookback, capital, candidate limit,
next run time, active search ID, and status. The default interval is 24 hours.
Database claiming prevents overlapping runs, and startup recovery makes an
interrupted schedule eligible to run again.

The account dashboard also runs manual backtests from a saved strategy. Users
choose pair, timeframe, date range, capital, fee, position size, Short support,
and risk exits. An explicit range loads the requested historical candles instead
of filtering only the latest chart data. The default cap is 20,000 range candles.
Results remain owned by the account, appear in a reloadable run history, and can
be filtered by P/L, direction, and exit reason. Schedule edits create immutable
configuration versions.

Strategy authoring accepts either a prompt or a public article URL. Article
downloads reject private addresses, redirects, non-text content, and responses
larger than 200 KB before sending extracted text to Gemini. Crawler selector
templates use the same account boundary. A Gemini repair stays in
`NEEDS_REVIEW` until the user confirms that exact version. Gemini calls remain
disabled while `GEMINI_API_KEY` is blank.

The final architecture proof matrix and repeatable commands are documented in
[`docs/architecture/PROOF_MATRIX.md`](docs/architecture/PROOF_MATRIX.md).

## Prerequisites

- Java 21
- Docker Engine and Docker Compose

The repository includes Maven Wrapper, so a system Maven installation is not
required.

## Build and verify

```bash
./mvnw clean verify
```

The system Maven command is also supported when Maven 3.6.3 or newer is
installed:

```bash
mvn clean verify
```

The integration-test phase uses Docker to validate Flyway, dispatch, worker
claim/lease, duplicate-delivery, and DLQ semantics against real PostgreSQL and
RabbitMQ containers.

## Run the current application

Start PostgreSQL and RabbitMQ:

```bash
docker compose up -d postgres rabbitmq
```

Build executable application jars:

```bash
./mvnw clean package
```

Run the API or worker in separate terminals:

```bash
java -jar api-app/target/api-app-0.1.0-SNAPSHOT-exec.jar
java -jar worker-app/target/worker-app-0.1.0-SNAPSHOT-exec.jar
```

Or run the complete container topology:

```bash
docker compose up --build
```

Change only the deployment replica count to demonstrate worker scaling:

```bash
docker compose up --build --scale worker=3
```

The `worker` service intentionally has no fixed `container_name` or host port.
All replicas consume the same durable `crypto.backtest.jobs` queue.

Open `http://localhost:8080` for the complete dashboard, or call:

```bash
curl "http://localhost:8080/api/v1/market/candles?symbol=BTCUSDT&timeframe=5m&limit=100"
curl "http://localhost:8080/api/v1/strategies"
curl "http://localhost:8080/api/v1/search-runs/capabilities"
curl "http://localhost:8080/api/v1/system/status"
curl "http://localhost:8080/api/v1/news?limit=20"
curl -X POST "http://localhost:8080/api/v1/news/collect"
curl "http://localhost:8080/actuator/health"
curl "http://localhost:8080/actuator/health/marketData"
curl "http://localhost:8080/actuator/health/newsProvider"
curl "http://localhost:8080/actuator/health/sentimentAnalyzer"
```

Account-owned endpoints use the session cookie returned by registration or
login:

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
POST   /api/v1/user-strategies/drafts
POST   /api/v1/user-strategies/drafts/{draftId}/confirm
GET    /api/v1/user-strategies
GET    /api/v1/user-strategies/{strategyId}
DELETE /api/v1/user-strategies/{strategyId}
POST   /api/v1/discovery-schedules
GET    /api/v1/discovery-schedules
GET    /api/v1/discovery-schedules/{scheduleId}
POST   /api/v1/discovery-schedules/{scheduleId}/stop
POST   /api/v1/discovery-schedules/{scheduleId}/start
PUT    /api/v1/discovery-schedules/{scheduleId}
GET    /api/v1/discovery-schedules/{scheduleId}/versions
GET    /api/v1/experiments/mine
POST   /api/v1/crawler-templates
GET    /api/v1/crawler-templates
GET    /api/v1/crawler-templates/{templateId}/versions
POST   /api/v1/crawler-templates/{templateId}/repair
POST   /api/v1/crawler-templates/{templateId}/versions/{version}/confirm
```

Create a schedule with ISO-8601 durations. Omitted values default to a one-year
lookback, 10,000 capital, 125 candidates, and a 24-hour interval:

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "lookback": "P365D",
  "initialCapital": 10000,
  "candidateLimit": 125,
  "interval": "PT24H"
}
```

M4 adds these synchronous single-candidate endpoints:

```text
POST /api/v1/experiments
GET  /api/v1/experiments/{experimentId}
GET  /api/v1/experiments/{experimentId}/provenance
POST /api/v1/experiments/{experimentId}/rerun
GET  /api/v1/leaderboard?searchRunId={searchRunId}&limit=50
```

`POST /api/v1/experiments` accepts the symbol, timeframe, immutable candle
dataset, strategy definitions, and combination policy. It is deliberately not
the M5 search endpoint: it executes one candidate locally and synchronously.
Creating a manual experiment now requires a session. Details, provenance, and
rerun also require the owning session when an experiment has an ownership row.
Legacy Search experiments remain public.

M5 exposes bounded search-generation, dispatch, worker execution, cancellation,
and live progress through:

```text
POST /api/v1/datasets
POST /api/v1/search-runs
GET  /api/v1/search-runs/{searchRunId}
POST /api/v1/search-runs/{searchRunId}/cancel
GET  /api/v1/search-runs/capabilities
```

Starting a search returns `202 Accepted` and a `Location` immediately. Candidate
generation runs on a bounded local executor and persists batches of at most the
requested `batchSize` (1–1000). The request must reference an existing immutable
market dataset and provide `randomSeed`, exact `strategyVersions`, parameter
choices, and at least one automatic stop condition. This executor is only the
generation boundary; it does not execute backtests. The response reports
`pendingDispatchJobs` separately from `queuedJobs`. Pending jobs remain pending
through broker failures and become queued only after publisher ACK confirmation.

Use `POST /api/v1/search-runs?generator=genetic` (or `random`) to replace the
generator for one run. `CRYPTO_SEARCH_GENERATOR` controls only the advertised
default. This does not modify the backtester, evaluator, ranking, controllers,
or leaderboard implementation. Search clients subscribe to
`/topic/search/{searchRunId}`; leaderboard clients subscribe to
`/topic/leaderboard/{searchRunId}` through the same `/ws` STOMP endpoint.

Operational metrics include active search runs, candidates generated, queue
depth, job starts/completions/failures/duration/duplicate delivery, market
reconnect/gap/UI latency, News/Sentiment failure and duration, and pending
outbox rows. They are exposed through `/actuator/metrics`; health components are
available under `/actuator/health`.

News collection is enabled by default and configured through
`NEWS_PROVIDER`, `NEWS_API_URL`, `NEWS_API_KEY`, and the timeout/schedule
properties under `crypto.news`. The CryptoCompare key is sent in the
`Authorization` header and must be stored in the git-ignored `.env` file, never
in `docker-compose.yml`. `SENTIMENT_PROVIDER=keyword` selects the default local
analyzer.
Failure counters and inference latency are exported as
`crypto.news.collection.failures`, `crypto.sentiment.inference.failures`, and
`crypto.sentiment.inference.duration`.

The browser subscribes to `/topic/market/{symbol}/{timeframe}` through the STOMP
endpoint `/ws`. Each of the four charts owns one subscription. Changing a
timeframe unsubscribes only that chart, fetches its candles, and subscribes to
the replacement topic without a page or backend restart. `CANDLE_UPDATE` events
replace a candle with the same `openTime` and append a candle with a new
`openTime`.

`GET /api/v1/strategies` is generated from the discovered strategy factories;
the controller has no baseline-strategy switch or concrete strategy dependency.
Adding a plugin means adding its `Strategy` and `StrategyFactory` bean. Strategy
configuration remains a flexible versioned map suitable for the existing JSONB
snapshot columns; adding the M7 MACD plugin requires no database schema change.

The backtester only passes candle prefixes to strategies and queues a signal
from candle N for execution at candle N+1 open. A final open position is valued
deterministically by liquidating it at the last candle close. This terminal
valuation rule is versioned by the engine and covered by tests.

`CRYPTO_MARKET_PROVIDER=binance|okx` selects one market adapter when the API
starts. Binance is the default. Configure provider endpoints with
`BINANCE_REST_URL`, `BINANCE_WEBSOCKET_URL`, `OKX_REST_URL`, and
`OKX_WEBSOCKET_URL`. Both adapters return the same REST and STOMP contracts, so
the dashboard does not change when the provider changes. The current OKX
symbol mapper supports the configured USDT pairs.

Compose publishes PostgreSQL on host port `55432` by default while containers
keep using port `5432` on the internal network. Set both `POSTGRES_PORT` and the
matching `DATABASE_URL` when running a Java process directly against a
different host port.

## Architecture proof commands

Run all six M7 change/failure/scaling proofs:

```bash
./scripts/verify-architecture-proofs.sh
```

Run the full quality gate and the deployable topology:

```bash
mvn clean verify
docker compose up --build
```

The proof script covers MACD plugin extension, Random-to-Genetic replacement,
worker one-to-three scaling without duplicate completion, News failure
isolation, Binance reconnect/gap recovery/duplicate protection, and linkage from
leaderboard rank #1 to complete immutable provenance. These are automated tests;
the Compose command separately verifies production application wiring and
startup.

## Module dependency direction

```text
api-app / worker-app -> infrastructure -> core
integration-tests    -> api-app / worker-app (test scope only)
```

- `core`: framework-independent domain, application services, and ports.
- `infrastructure`: persistence, messaging, and external provider adapters.
- `api-app`: REST, WebSocket, dashboard, and API bootstrap.
- `worker-app`: independently scalable backtest worker bootstrap.
- `integration-tests`: cross-module integration, failure, and proof tests.

Architecture details and current/planned boundaries are under `docs/architecture`
and `docs/adr`.
