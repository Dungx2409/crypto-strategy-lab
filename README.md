# Crypto Strategy Lab

Crypto Strategy Lab is a research and backtesting application for cryptocurrency
strategies. It streams market candles, combines technical and news-based signals,
searches strategy configurations, and stores reproducible backtest results. It is
for study and experimentation only. It does not place real orders.

The application is a Java 21 modular system with a Spring Boot API, a separate
backtest worker, PostgreSQL, RabbitMQ, and a browser dashboard.

## Screenshots

### Realtime market charts

![Realtime market charts](docs/screenshots/realtime-dashboard.png)

### Strategy discovery

![Strategy discovery](docs/screenshots/discovery-dashboard.png)

### Backtest results

![Backtest results](docs/screenshots/backtest-dashboard.png)

## Implemented features

- Session accounts with BCrypt password hashing and account-owned data.
- Four independent real-time candlestick charts for different timeframes, backed
  by Java `HttpClient` REST and WebSocket adapters for Binance or OKX.
- Interactive canvas charts with paged earlier history, wheel zoom, drag pan,
  crosshair/OHLC inspection, volume, and continuously changing open candles.
- Binance or OKX market provider selection.
- MA, RSI, Bollinger Bands, Support/Resistance, MACD, News Sentiment, and a safe
  executable `RULE` strategy DSL for AI-authored metric/operator conditions.
- Majority and weighted signal-combination policies.
- Random Search and multi-generation Genetic Search.
- Durable job queue, scalable workers, retry, cancellation, and outbox/inbox
  processing.
- Deterministic backtesting with immutable datasets and provenance. Result charts
  reload the experiment's own candles and display indicator, signal, entry, and
  exit overlays rather than using the current realtime chart.
- Long and Short positions, position sizing, Stop Loss, Take Profit, and Trailing
  Stop.
- Performance metrics, trade history, filters, chart markers, and leaderboards.
- Manual account-owned backtests with reloadable history and exact historical
  date ranges up to 20,000 candles by default.
- Natural-language and public article URL strategy authoring through Gemini.
  Generated JSON can define validated `RULE` logic or compose registered
  plugins; it cannot run arbitrary generated Java code.
- Strategy idea confirmation, JSON repair attempts, smoke tests, version history,
  and deletion.
- Account-owned continuous Genetic Search schedules with start, stop, recovery,
  and immutable configuration versions. The default interval is 24 hours.
- News collection with deterministic keyword sentiment by default and optional
  Gemini semantic sentiment with versioned model/preprocessing metadata.
- Versioned crawler selector templates, scheduled live-page selector checks, and
  Gemini-assisted replacement selectors that remain `NEEDS_REVIEW` until the
  account owner confirms them.
- Flyway database migrations V1 through V19.
- A real-time load proof in which 1,000 of 1,000 sessions received all four
  timeframe topics.

## Deliberate limits and not fully verified

- Automatic runtime failover from Binance to OKX. Select one provider at startup.
- Arbitrary generated Java source is deliberately not compiled or executed.
  Natural-language authoring can create new executable logic through the
  validated `RULE` DSL or compose registered plugins, without rebuilding the app.
- Each experiment uses one selected timeframe from the supported set. Strategies
  that synchronize signals from several timeframes inside one experiment are not
  implemented.
- Real trading and exchange order execution. The application is a backtester.
- A literal 24-hour soak test. Schedule recovery and repeated execution have
  automated coverage, but a full-day run has not been recorded.
- Automated browser coverage for navigation, chart updates, and trade selection.
- Tablet and mobile visual QA.

Gemini strategy authoring, semantic sentiment, and selector repair remain disabled
until `GEMINI_API_KEY` is set. Live CryptoCompare collection similarly needs
`NEWS_API_KEY`; deterministic keyword sentiment does not.

## Run with Docker

Requirements:

- Docker Engine with Docker Compose
- Internet access for Maven dependencies and live market data

Create local configuration:

```bash
cp .env.example .env
```

The defaults run without Gemini and without authenticated CryptoCompare news.
Leave those keys blank, or add them to `.env`:

```dotenv
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.7-flash
NEWS_API_KEY=
SENTIMENT_PROVIDER=keyword
CRAWLER_CHECK_INTERVAL=15m
```

Set `SENTIMENT_PROVIDER=gemini` to use Gemini semantic sentiment. Selector checks
run every 15 minutes by default; a failed check never activates an AI proposal
without user confirmation.

Build and start the complete application:

```bash
docker compose up -d --build
docker compose ps
```

Open `http://localhost:8080`. RabbitMQ management is available at
`http://localhost:15672` with the credentials from `.env`.

Stop the application without deleting its database volume:

```bash
docker compose down
```

To run three worker replicas:

```bash
docker compose up -d --build --scale worker=3
```

## Use the application

1. Open the dashboard and register an account or sign in.
2. Use **Realtime** to select a market pair and inspect four independently
   updating timeframes.
3. Use **Strategy** to create a strategy from a prompt or article URL. This step
   requires `GEMINI_API_KEY`. Confirm the proposed idea before saving its JSON.
4. Use **Discovery** to run Random or Genetic Search, or create a repeating
   Genetic schedule. Choose the pair, timeframe, lookback, capital, and candidate
   limit.
5. Use **Backtest** to select a saved strategy, date range, fee, position size,
   Short support, and risk exits. Review metrics, trades, and chart markers after
   the run completes.
6. Use **News** to collect and inspect news sentiment. Live CryptoCompare data
   requires `NEWS_API_KEY`.
7. Return to Discovery or Backtest later to reload account-owned schedules,
   experiments, versions, and results.

## Develop and test

Java 21 is required. The Maven Wrapper is included:

```bash
./mvnw clean verify
```

Build executable jars:

```bash
./mvnw clean package
```

Run the architecture proof suite:

```bash
./scripts/verify-architecture-proofs.sh
```

Module dependency direction:

```text
api-app / worker-app -> infrastructure -> core
integration-tests    -> api-app / worker-app
```

- `core`: domain models, application services, and ports.
- `infrastructure`: database, messaging, and external provider adapters.
- `api-app`: REST, WebSocket, dashboard, and API startup.
- `worker-app`: independently scalable backtest worker.
- `integration-tests`: cross-module and failure-path tests.

## Detailed documentation

- [Product requirements](docs/requirements/Crypto%20Strategy%20Lab%20%E2%80%93%20%C4%90%E1%BB%93%20%C3%A1n%20cu%E1%BB%91i%20k%E1%BB%B3.md)
- [Additional requirements](docs/requirements/new_add_requirement.txt)
- [Architecture](docs/ARCHITECTURE.md)
- [Requirements traceability](docs/REQUIREMENTS_TRACEABILITY.md)
- [Architecture proof matrix](docs/architecture/PROOF_MATRIX.md)
