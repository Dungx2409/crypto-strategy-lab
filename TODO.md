# Next tasks

## Newly added requirements

- [x] Add registration, login, logout, and account identity with server-side sessions and BCrypt credentials.
- [x] Add account-owned storage and deletion for user-created strategies.
- [x] Add natural-language strategy authoring with idea confirmation, generated-strategy testing, repair attempts, versioning, and safe execution.
- [ ] Add manual backtest inputs for strategy, pair, date range, capital, and execution settings, plus result filters and reports.
- [x] Add continuous discovery scheduling, stop controls, saved versions, and a 24-hour leaderboard loop using non-exhaustive search.
- [ ] Prove the realtime design for 1,000 browser connections and four charts per user with a load test and measurements.
- [x] Stream the last open candle as a changing realtime candle while completed candles remain fixed.
- [ ] Finish TradingView-like chart and history visual QA in a connected browser.
- [ ] Add versioned crawler extraction templates and LLM-assisted selector repair.
- [x] Use Gemini as the current non-OpenAI model for strategy authoring.

## Current verification

- [x] Record the final `mvn clean verify` rerun after the search lifecycle correction.
- [x] Run `scripts/verify-architecture-proofs.sh` with PostgreSQL and RabbitMQ Testcontainers.
- [ ] Open the running dashboard in a connected browser at 1680 by 945 and compare each core screen with the supplied images.
- [ ] Check responsive behavior at tablet and mobile widths.
- [ ] Set `NEWS_API_KEY` in the local `.env` file and collect real CryptoCompare news before the course demo. The provider returns HTTP 401 without a key.
- [x] Run one complete search with the API and worker containers and inspect the public leaderboard and experiment APIs.
- [x] Rebuild the final API and worker images and verify the public `RUNNING` to `EVALUATING` to `COMPLETED` lifecycle.

## Remaining architecture work

- [x] Add Long/Short execution to the deterministic backtest engine with explicit trade direction and persistence.
- [x] Add percentage Position Sizing to immutable execution configuration.
- [x] Add versioned Stop Loss and Take Profit execution rules with deterministic OHLC ordering.
- [x] Add Trailing Stop with a versioned high-water or low-water rule.
- [x] Add multiple coin pairs through a configurable application allow-list.
- [x] Define a historical sentiment dataset contract and add `NewsSentimentStrategy` to reproducible backtests.
- [x] Feed evaluated fitness back into the Genetic generator through a framework-neutral port.
- [ ] Add an automated browser test for navigation, independent chart changes, and trade selection when a browser runner is available.

## Optional extensions outside the written MVP

- [x] Add OKX as a startup-selectable second exchange behind `MarketDataProvider` without changing frontend contracts.
- [x] Prompt strategy authoring through Gemini, with schema validation, versioning, and a safe execution model.
- [ ] Article URL strategy authoring through Gemini.
- [ ] Self-healing LLM news extraction with versioned templates and review controls.
- [x] Add a versioned SentimentStrategy plugin if the course demo chooses to include news as a trading signal.
- [x] Add 30m, 2h, and 1d after the five required demo timeframes are stable.
