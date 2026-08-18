# Crypto Strategy Lab

Crypto Strategy Lab is a Java 21 research platform for experimenting with
reproducible cryptocurrency trading strategies. It is a university Software
Architecture project; it does not place real orders or promise profitable
results.

The normative product and architecture contract is
[`FEATURE_SPEC.md`](FEATURE_SPEC.md). The approved delivery order is recorded
in [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md).

## Current status

M7 is implemented. The browser dashboard now uses only backend contracts to show
market candles, discovered strategy plugins, search configuration/progress,
leaderboard, experiment artifacts and immutable provenance, News + Sentiment,
and independent system status. It materializes the current candle snapshot as an
immutable checksummed dataset before starting a search; no synthetic dashboard
data is used.

Random and Genetic generators are both registered behind `StrategyGenerator` and
can be selected per search request. The worker image remains replica-safe and
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
endpoint `/ws`. Changing timeframe unsubscribes the old market topic, fetches
only the selected candles, and subscribes to the replacement topic without a
page/backend restart.

`GET /api/v1/strategies` is generated from the discovered strategy factories;
the controller has no baseline-strategy switch or concrete strategy dependency.
Adding a plugin means adding its `Strategy` and `StrategyFactory` bean. Strategy
configuration remains a flexible versioned map suitable for the existing JSONB
snapshot columns; adding the M7 MACD plugin requires no database schema change.

The backtester only passes candle prefixes to strategies and queues a signal
from candle N for execution at candle N+1 open. A final open position is valued
deterministically by liquidating it at the last candle close. This terminal
valuation rule is versioned by the engine and covered by tests.

Binance public endpoints are configurable with `BINANCE_REST_URL` and
`BINANCE_WEBSOCKET_URL`. Compose publishes PostgreSQL on host port `55432` by
default while containers keep using port `5432` on the internal network. Set
both `POSTGRES_PORT` and the matching `DATABASE_URL` when running a Java process
directly against a different host port.

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
