# Architecture summary

Crypto Strategy Lab is a research and backtesting system. It collects market
and news data, builds strategy candidates, runs deterministic backtests, ranks
the results, and stores enough evidence to reproduce each result. It does not
place real exchange orders.

## System at a glance

```text
User
  |
  | browser, REST, STOMP WebSocket
  v
Dashboard inside api-app
  |
  +--------------------> PostgreSQL
  |                      accounts, candles, strategies, datasets,
  |                      jobs, results, leaderboard, news, outbox, inbox
  |
  +----> RabbitMQ ----> worker-app replicas
  |       jobs/events    deterministic backtests
  |
  +----> Binance or OKX
  |       candles and realtime updates
  |
  +----> CryptoCompare or HTML websites
  |       news articles
  |
  +----> Gemini
          strategy authoring, sentiment, selector repair
```

PostgreSQL is the durable source of truth. RabbitMQ moves backtest jobs and
domain events. API and worker processes share the same domain rules from
`core`.

## Code modules

| Module | Responsibility |
|---|---|
| `core` | Domain types, application services, ports, strategy rules, search, backtesting, evaluation, and ranking |
| `infrastructure` | JDBC, RabbitMQ, Binance, OKX, CryptoCompare, HTML extraction, Gemini, BCrypt, and runtime strategy compilation |
| `api-app` | REST endpoints, sessions, STOMP topics, scheduled work, health checks, and the browser dashboard |
| `worker-app` | Claims queued jobs and runs deterministic backtests outside the API process |
| `integration-tests` | Tests PostgreSQL, RabbitMQ, migrations, worker behavior, failure recovery, and architecture proofs |

Dependencies point toward the domain:

```text
api-app -----> infrastructure -----> core
worker-app --> infrastructure -----> core
integration-tests --> runnable modules in test scope
```

`core` does not depend on Spring MVC, JDBC, AMQP, exchange payloads, or browser
code. This keeps business rules usable from both API and worker processes.

## Main feature boundaries

### Accounts

The API stores BCrypt password hashes and creates HTTP-only server sessions.
Account ownership protects saved strategies, schedules, crawler templates, and
manual backtest history. The browser hides the dashboard until registration or
login succeeds.

### Market data

One startup-selected adapter connects to Binance or OKX. Provider payloads are
converted to the shared `Candle` and `CandleUpdate` types before entering the
application. Each of the four charts owns its timeframe and STOMP subscription.
An update with the same candle open time replaces the last candle. A later open
time appends a candle. Only closed candles enter durable history.

### Strategies and search

Strategies are plugins registered through `StrategyFactory`. The registry
currently includes technical indicators, news sentiment, MACD, and validated
user rules. Combination policies decide how several signals become one BUY,
SELL, or HOLD result.

Random and Genetic generators both produce the same candidate contract. Search
persists candidates in bounded batches and stops by configured limits. The
backtester, evaluator, ranking code, and worker do not depend on a concrete
generator.

### Experiments and leaderboard

An experiment contains an immutable dataset, strategy definitions, combination
policy, execution settings, generator data, and software versions. The engine
uses completed candle prefixes and fills a signal on the next candle open. This
prevents look-ahead bias.

Evaluation calculates return, win rate, maximum drawdown, trade count, and a
score. Ranking writes a leaderboard projection linked to the original
experiment ID. Details, trades, signals, and provenance remain available after
ranking.

### News and sentiment

`NewsProvider` hides the source behind one normalized `NewsItem` contract.
CryptoCompare is the API provider. Account-owned HTML templates can also fetch
pages and extract articles with Jsoup selectors. Gemini may propose replacement
selectors, but a user must confirm a `NEEDS_REVIEW` version before activation.

The sentiment analyzer is replaceable. Stored predictions include model,
model version, input version, and preprocessing version. News and sentiment
failures do not stop market data, search, backtests, or the leaderboard.

## Runtime flows

### Realtime candles

```text
Exchange WebSocket
  -> market adapter
  -> CandleUpdate
  -> shared market stream
  -> STOMP topic
  -> one chart state
  -> replace last candle or append new candle
```

Backend subscriptions are shared by pair and timeframe. Four thousand chart
subscriptions for 1,000 users can share four provider streams when those users
watch the same four topics.

### Search and backtest

```text
Dashboard candle snapshot
  -> immutable dataset and checksum
  -> Random or Genetic generator
  -> candidate, experiment, job, and outbox transaction
  -> RabbitMQ job queue
  -> worker claim and backtest
  -> BacktestCompleted event
  -> evaluation
  -> StrategyEvaluated event
  -> leaderboard projection
  -> REST and STOMP update
```

The API marks a job queued only after RabbitMQ confirms publication. A worker
commits results before acknowledging delivery.

### Natural-language strategy authoring

```text
Prompt or public article URL
  -> Gemini strategy idea
  -> user confirmation
  -> restricted JSON
  -> registry and parameter validation
  -> deterministic Java generated from validated RULE fields
  -> source equality check, compile, and smoke test
  -> account-owned strategy version
```

The application never compiles raw model output. It generates Java from allowed
metrics, operators, periods, and thresholds. Edited source fails validation.

### HTML news collection

```text
Saved active selectors
  -> bounded public page download
  -> Jsoup article extraction
  -> normalized NewsItem records
  -> sentiment analysis
  -> PostgreSQL
```

The scheduled monitor checks active selectors and collects articles. A failed
selector check can create a Gemini repair proposal, but cannot activate it.

## Data consistency and failure handling

- Flyway owns the PostgreSQL schema and applies migrations at startup.
- Immutable dataset checksums and versioned configuration support reruns.
- Transactional outbox rows prevent database commits from losing messages.
- Inbox rows make event consumers idempotent under duplicate delivery.
- Worker database leases allow another worker to recover abandoned jobs.
- Failed jobs use bounded retries and then move to the dead-letter queue.
- Search cancellation removes work that has not started and lets active work
  finish safely.
- Market reconnect performs gap recovery and ignores duplicate closed candles.
- News errors change News health only. They do not enter trading paths.

## Scaling and deployment

Docker Compose runs four services:

```text
api + worker replicas + PostgreSQL + RabbitMQ
```

The API handles browser traffic, provider connections, and orchestration. Worker
replicas have no public port and consume the same durable queue. Scaling workers
changes the replica count, not the application code or queue contract.

## Extension points

- Add an exchange by implementing `MarketDataProvider`.
- Add a strategy through `Strategy` and `StrategyFactory`.
- Add a search method behind `StrategyGenerator`.
- Add a news source behind `NewsProvider`.
- Add a sentiment model behind `SentimentAnalyzer`.
- Add another event consumer with its own inbox identity.

These contracts keep provider details and concrete algorithms out of consumers.

## Security boundaries

- Passwords use BCrypt hashes. Browser sessions use HTTP-only cookies.
- Secrets come from `.env` or deployment environment variables.
- Article and crawler downloads reject private and local addresses.
- AI responses pass schema, registry, parameter, and smoke-test checks.
- Generated Java comes only from validated rule fields and is checked before
  compilation.
- This application handles research data only. It has no wallet keys or order
  execution permission.

## Known limits

- API sessions live in process memory. Multiple API replicas need sticky
  sessions or a shared session store.
- Market provider replacement requires an API restart. Automatic failover and
  exchange aggregation are not implemented.
- Each experiment uses one timeframe, although the dashboard can show four
  independent timeframes.
- The product backtests strategies but never sends real orders.
- Environment-specific load and long-duration tests are still required before a
  production deployment.

## Detailed documents

- [Full architecture decisions](../ARCHITECTURE.md)
- [System context](C4_CONTEXT.md)
- [Container view](C4_CONTAINER.md)
- [Runtime sequence views](DYNAMIC_VIEW.md)
- [Domain event catalog](EVENT_CATALOG.md)
- [Architecture proof matrix](PROOF_MATRIX.md)
- [Architecture decision records](../adr/ADR-001-market-data-adapter.md)
