# Crypto Strategy Lab architecture

Crypto Strategy Lab is a research app for testing crypto trading strategies. It collects market candles and news, lets users create or save strategies, runs deterministic backtests, ranks results, and keeps account-owned history. It does not place real trades.

## Main modules

```text
Browser
  |
  | REST, WebSocket/STOMP
  v
api-app
  |
  v
core ports and application services
  ^
  |
infrastructure adapters
  |
  | PostgreSQL, RabbitMQ, Binance or OKX, CryptoCompare, Gemini
  v
worker-app
```

- `core` contains the domain model, strategy contracts, backtest engine, discovery services, and ports.
- `infrastructure` implements persistence, queues, market providers, news providers, and Gemini adapters.
- `api-app` exposes REST APIs, WebSocket updates, account sessions, and the static dashboard.
- `worker-app` consumes queued backtest jobs so discovery can scale separately from the API.

## Key flows

Realtime market data:

```text
Binance or OKX -> market adapter -> CandleUpdate -> STOMP -> chart state
```

Manual backtest:

```text
User account -> selected strategy and period -> deterministic engine -> metrics -> account history
```

Automatic discovery:

```text
Discovery schedule -> genetic search -> queued backtests -> worker results -> leaderboard -> schedule latest result link
```

News and sentiment:

```text
CryptoCompare or HTML crawler -> stored article -> sentiment model -> versioned sentiment observation
```

## Current behavior to know

- Users must register or log in before they can access account-owned dashboard features.
- Default accounts exist for local testing.
- Manual backtest history shows the symbol, timeframe, strategy, start time, return, win rate, drawdown, trades, capital, dataset range, and experiment ID.
- Automatic discovery schedules store both active search ID and latest finished search ID. The UI has an "Open result" action so the user can see what the background run found.
- The news provider dropdown can choose CryptoCompare, crawler providers, or all providers.
- HTML crawler selector versions are stored in the database. Gemini can propose repairs, but the user must confirm before a repaired version becomes active.
- Example and test news rows are hidden from the article list.

## Important limits

- Natural-language strategy authoring creates safe JSON and `AI_DSL@1.0` rules. It does not compile arbitrary generated Java files.
- Gemini features need `GEMINI_API_KEY`.
- CryptoCompare collection needs `CRYPTOCOMPARE_API_KEY` for real provider testing.
- Continuous discovery has tests and persisted schedule state, but a true 24-hour proof requires running the deployment for 24 hours.
- Market provider selection is made at API startup. The app does not merge Binance and OKX candles.
