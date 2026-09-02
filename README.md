# Crypto Strategy Lab

Crypto Strategy Lab is a research and backtesting app for cryptocurrency
strategies. It streams market candles, combines technical and news-based signals,
searches strategy configurations, and stores reproducible backtest results. It is
for study and experimentation only. It does not place real orders.

The stack is Java 21 modules with a Spring Boot API, a separate backtest worker,
PostgreSQL, RabbitMQ, and a browser dashboard.

## Screenshots

### Account gate

![Register or login](docs/screenshots/auth-gate.png)

### Realtime market charts

![Realtime market charts](docs/screenshots/realtime-dashboard.png)

### Strategy discovery

![Strategy discovery](docs/screenshots/discovery-dashboard.png)

### Backtest results

![Backtest results](docs/screenshots/backtest-dashboard.png)

## Implemented features

- A first-visit account gate. The dashboard stays hidden until register or login
  succeeds. Sessions use an HTTP-only cookie and BCrypt password hashes.
- A configurable local demo account (`demo` / `crypto-demo` by default).
- Six signed-in views: Realtime, Strategy Engine, Discovery, Backtest, News
  Crawler, and Settings.
- Settings shows logout only while signed in, plus market, news, sentiment,
  queue, worker, and outbox health.
- Four independent realtime candlestick charts. Market data comes from Java
  `HttpClient` REST and WebSocket adapters. Choose Binance or OKX with
  `CRYPTO_MARKET_PROVIDER` at startup (not from the UI).
- A custom pair picker with CryptoCompare coin icons for BTCUSDT, ETHUSDT,
  SOLUSDT, and BNBUSDT. Chart prices, MA, volume, and OHLC hover values use two
  decimal places.
- Interactive canvas charts with earlier history paging, wheel zoom, drag pan,
  crosshair and OHLC inspection, volume, MA(20), and live open-candle updates.
- Strategy plugins: `MA`, `RSI`, `BB`, `SR`, `MACD`, `NEWS_SENTIMENT`, plus
  bounded executable `RULE` and `AI_DSL@1.0` languages. Per-plugin Fine tune
  controls parameter search ranges and voting weight.
- Majority and weighted signal-combination policies.
- Manual Discovery with Random Search or Genetic Search, Quick (25) / Balanced
  (125) / Deep (500) size presets, and advanced stop conditions.
- A Discovery leaderboard with sort by score, return, win rate, max drawdown,
  trades, or rank, in either direction. Selecting a row opens the experiment on
  Backtest.
- Account-owned Automatic discovery schedules. Each tick always runs Genetic
  Search. Start, stop, edit, recover interrupted runs, and keep immutable
  configuration versions. Open the Versions panel on a schedule row to list each
  saved config. Use Open result to load that schedule's latest search run into
  the Discovery leaderboard.
- Durable job queue, scalable workers, retry, cancellation, and outbox/inbox
  processing.
- Deterministic backtesting with immutable datasets and provenance. Result charts
  reload the experiment's own candles and draw indicator, signal, entry, and exit
  overlays.
- Long and short positions, position sizing, stop loss, take profit, and trailing
  stop. Risk profiles: Standard, Conservative, Active, or Custom.
- Performance metrics, trade history, P/L and exit filters, chart markers, and
  account-owned manual run history with exact date ranges up to 20,000 candles by
  default.
- Natural-language and public article URL strategy authoring through Gemini.
  Flow: Generate idea → Confirm and build code (includes a 250-candle smoke test)
  → Save tested strategy. Output is bounded Trading DSL or registered plugins. It
  cannot run arbitrary generated Java, JavaScript, or Groovy. `AI_DSL` strategies
  can be saved for Backtest; they are not offered in the Discovery plugin catalog.
- News collection from CryptoCompare, RSS or Atom feeds, and HTML websites.
  Composite `all` mode merges CryptoCompare and RSS and tolerates one provider
  failure. Sentiment uses the local keyword model by default, or Gemini when
  selected. The News screen also sets crawl interval and coin filter.
- Versioned crawler selector templates, scheduled live-page selector checks, and
  Gemini-assisted replacement selectors that stay `NEEDS_REVIEW` until the account
  owner confirms them.
- Flyway migrations V1 through V22.

## Deliberate limits and not fully verified

- Automatic runtime failover from Binance to OKX. Pick one provider at startup.
  The realtime UI label stays on Binance even if OKX is configured.
- Arbitrary generated Java source is not compiled or executed. Authoring creates
  logic only through bounded `AI_DSL` or registered plugins.
- Each experiment uses one timeframe. Multi-timeframe signal sync inside one
  experiment is not implemented.
- Real trading and exchange order execution. This app is a backtester.
- Automatic discovery runs are account-owned and tracked, but their results do not
  replace the main Discovery leaderboard until you press Open result on that
  schedule.
- A literal 24-hour soak test. Schedule recovery and repeated execution have
  automated coverage. A full-day run has not been recorded.
- Automated browser coverage for navigation, chart updates, and trade selection.
- Tablet and mobile visual QA.

Gemini strategy authoring, semantic sentiment, and selector repair need
`GEMINI_API_KEY`. Live CryptoCompare collection needs `NEWS_API_KEY`. Keyword
sentiment does not need an external key. The checked-in example leaves both keys
blank.

## Run with Docker

Requirements:

- Docker Engine with Docker Compose
- Internet access for Maven dependencies and live market data

Create local configuration:

```bash
cp .env.example .env
```

The defaults run without Gemini and without authenticated CryptoCompare news.
The default local account is `demo` with password `crypto-demo`. Change or
disable it in `.env` before exposing the app outside a local machine:

```dotenv
DEFAULT_ACCOUNT_ENABLED=true
DEFAULT_ACCOUNT_USERNAME=demo
DEFAULT_ACCOUNT_PASSWORD=crypto-demo
GEMINI_API_KEY=
GEMINI_MODEL=gemini-2.5-flash
NEWS_API_KEY=
NEWS_PROVIDER=all
NEWS_RSS_URLS=https://www.coindesk.com/arc/outboundfeeds/rss/,https://cointelegraph.com/rss
SENTIMENT_PROVIDER=keyword
CRAWLER_CHECK_INTERVAL=15m
CRYPTO_MARKET_PROVIDER=binance
CRYPTO_MARKET_SUPPORTED_SYMBOLS=BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT
CRYPTO_MARKET_MAXIMUM_RANGE_CANDLES=20000
DISCOVERY_POLL_INTERVAL=1m
SESSION_COOKIE_SECURE=false
```

Set `SENTIMENT_PROVIDER=gemini` for Gemini semantic sentiment. Selector checks
run every `CRAWLER_CHECK_INTERVAL` (default 15 minutes). A failed check never
activates an AI proposal without user confirmation.

`CRYPTO_SEARCH_GENERATOR` (default `random` in Compose) sets the preferred
manual Discovery method when both Random and Genetic are available. Automatic
discovery schedules always use Genetic Search.

Choose the news provider on the News screen and click **Apply**. The choice
affects manual and scheduled collection immediately. `NEWS_PROVIDER` sets the
initial dropdown value:

```dotenv
# CryptoCompare only
NEWS_PROVIDER=cryptocompare

# RSS and Atom feeds only
NEWS_PROVIDER=rss

# CryptoCompare and RSS together
NEWS_PROVIDER=all
```

`rss` and `all` need at least one comma-separated URL in `NEWS_RSS_URLS`. The
older value `composite` is accepted as an alias for `all`. HTML website templates
are managed from the News screen and can run alongside any mode.

Build and start the full stack:

```bash
docker compose up -d --build
docker compose ps
```

Open `http://localhost:8080`. RabbitMQ management is at
`http://localhost:15672` with the credentials from `.env`.

Stop without deleting the database volume:

```bash
docker compose down
```

Run three worker replicas:

```bash
docker compose up -d --build --scale worker=3
```

## Use the application

1. Open `http://localhost:8080`. Only the account form is shown until register or
   login succeeds. Register a new account or sign in with `demo` / `crypto-demo`.
2. **Realtime.** Pick a pair (icons included), set the primary timeframe, and
   inspect four independently updating charts.
3. **Strategy Engine.** Tick the plugins you want, choose majority or weighted
   combine, and open Fine tune when you need parameter ranges or voting weights.
   To author with Gemini, write a prompt or paste an article URL, then Generate
   idea → Confirm and build code → Save tested strategy. This path needs
   `GEMINI_API_KEY`.
4. **Discovery.** Run Random or Genetic Search with Quick, Balanced, or Deep.
   Raw limits stay under Advanced search settings. Sort the leaderboard as needed.
   Under Automatic discovery, create a repeating Genetic schedule, then use Open
   result or Versions on each saved schedule row.
5. **Backtest.** Choose a saved strategy, period, and risk profile. Open Advanced
   backtest settings for custom dates, fees, sizing, or exits. Review metrics,
   trades, filters, chart markers, and provenance. Reload earlier runs from Saved
   manual runs.
6. **News Crawler.** Collect and inspect news sentiment. Live CryptoCompare data
   needs `NEWS_API_KEY`. Add HTML websites under Add a news website when you want
   custom sources.
7. **Settings.** Logout, and check system health for market data, news,
   sentiment, queue depth, workers, and outbox.

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
- [Architecture summary](docs/architecture/ARCHITECTURE_SUMMARY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Requirements traceability](docs/REQUIREMENTS_TRACEABILITY.md)
- [Architecture proof matrix](docs/architecture/PROOF_MATRIX.md)
