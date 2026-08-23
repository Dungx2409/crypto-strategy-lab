# Progress

## 2026-08-23

### Added replaceable Binance and OKX market providers

- Added an OKX adapter for historical candles and realtime open or closed candle updates.
- Kept OKX symbols, interval codes, pagination, DTOs, and subscription messages inside the adapter.
- Added `CRYPTO_MARKET_PROVIDER=binance|okx`; Binance remains the default.
- Added startup selection, payload mapping, provider contract, and DTO isolation tests.
- Confirmed the public OKX endpoint was available.
- Rebuilt the API with OKX selected and loaded three BTCUSDT candles through the unchanged `/api/v1/market/candles` endpoint with `degraded=false`.
- Restored the running API to Binance after the replacement test.
- The final `mvn clean verify` gate passed.
- The named architecture proof script passed with PostgreSQL and RabbitMQ Testcontainers.
- Browser visual and responsive checks remain pending because no browser connection was available.

### Added session-based accounts

- Added username registration, login, logout, and current-account endpoints.
- Normalized usernames for case-insensitive identity and database uniqueness.
- Stored BCrypt hashes with cost 12 instead of passwords.
- Added HTTP-only, SameSite Strict session cookies with an eight-hour timeout.
- Kept existing market and leaderboard APIs public. New account-owned APIs will require the session identity guard.
- Added Flyway V14 for accounts.
- Core account tests, BCrypt tests, controller session tests, migration tests, and PostgreSQL repository tests passed.

## 2026-08-21

### Added the remaining documented timeframes

- Added 30m, 2h, and 1d to the shared domain enum.
- Added the same choices to every independent chart and the primary timeframe buttons.
- Kept provider mapping generic because Binance uses the same interval codes.
- Domain contract tests: 3 passed.
- Reference dashboard tests: 2 passed.
- JavaScript syntax and diff checks passed.

### Added Long and Short backtesting

- Added explicit `LONG` and `SHORT` trade direction.
- Added engine version 2 while keeping version 1 valid for old long-only reruns.
- SELL opens a Short when shorting is enabled. BUY closes it on the next candle open.
- Dataset-end liquidation now handles either direction.
- Preserved the original Long arithmetic so old deterministic results do not drift.
- Added Flyway V10 to backfill and constrain trade direction.
- Added direction to JDBC writes, reads, REST results, trade tables, and chart markers.
- Added backend capabilities for engine version and fill policy so the browser does not hardcode them.
- Core tests: 56 passed.
- API, infrastructure, migration, persistence, worker, and architecture tests passed through `mvn clean verify`.

### Added percentage position sizing

- Added `positionSizePct` to immutable execution configuration with a 100% backward-compatible default.
- Added engine version 3 and kept versions 1 and 2 valid for old experiments.
- Positions commit only the selected percentage of available cash.
- Uncommitted cash remains part of portfolio equity and ending capital.
- Added the position size control to the Backtest screen and search request.
- Added a deterministic 50% sizing test.
- Changed the worker scaling proof to compare 9 jobs against 9 jobs, removing an unfair workload difference that caused timing flakes.
- The final `mvn clean verify` gate passed.

### Added Stop Loss and Take Profit

- Added optional Stop Loss and Take Profit percentages to immutable execution configuration.
- Added engine version 4 and kept versions 1 through 3 valid for old experiments.
- Applied risk exits after next-open execution and before candle-close analysis.
- Defined Stop Loss first when one OHLC candle touches both thresholds.
- Defined gap fills at candle open and normal fills at the configured level.
- Added `SIGNAL`, `STOP_LOSS`, `TAKE_PROFIT`, and `DATASET_END` exit reasons.
- Added Flyway V11 and updated both JDBC persistence paths.
- Added controls and exit reasons to the Backtest screen.
- Added deterministic stop-ordering and take-profit tests.
- The final `mvn clean verify` gate passed.

### Added Trailing Stop

- Added optional `trailingStopPct` to immutable execution configuration.
- Added engine version 5 while retaining versions 1 through 4 for old experiments.
- Long positions trail completed-candle highs. Short positions trail completed-candle lows.
- The current candle can trigger the previous trailing level but cannot tighten and trigger itself.
- Added `TRAILING_STOP` to persisted trade exit reasons through Flyway V12.
- Added a Trailing Stop control to the Backtest screen.
- Added a deterministic completed-water-mark test.
- The final `mvn clean verify` gate passed.

### Verified the extension batch at runtime

- Rebuilt the API and worker images with engine version 5 and Flyway V12.
- Loaded live Binance candles for 30m, 2h, and 1d through the public market API.
- Ran a 60-candidate search with Short enabled, 50% sizing, Stop Loss, Trailing Stop, and Take Profit.
- Observed `RUNNING`, `EVALUATING`, then `COMPLETED`.
- Completed all 60 jobs with no failures.
- Top-ten results contained both Long and Short trades.
- Runtime exit reasons included `SIGNAL`, `STOP_LOSS`, `TRAILING_STOP`, `TAKE_PROFIT`, and `DATASET_END`.

### Added multiple coin pairs

- Added ETHUSDT, SOLUSDT, and BNBUSDT to the dashboard pair selector.
- Moved the supported-pair allow-list to `CRYPTO_MARKET_SUPPORTED_SYMBOLS`.
- Added the setting to `.env.example` and Docker Compose.
- Pair changes update all four chart labels, REST slices, and STOMP subscriptions.
- Reference dashboard tests passed.
- Live 5m API requests returned candles for ETHUSDT, SOLUSDT, and BNBUSDT.

### Added reproducible News Sentiment strategy input

- Added normalized sentiment observations with source, time, score, model, input, and preprocessing versions.
- Included observations in immutable dataset checksums while preserving old candle-only checksums.
- Added Flyway V13 and persistence in both materialization and worker retrieval paths.
- Added observations to the dataset API and browser materialization request.
- Filtered strategy context by candle close time to prevent future-news look-ahead.
- Added `NEWS_SENTIMENT@1.0` with a configurable time window and buy or sell thresholds.
- Registered the strategy through the existing plugin factory contract.
- Added checksum, persistence, time-safety, strategy, factory, API, and migration tests.
- The final `mvn clean verify` gate passed.
- Rebuilt API and worker images with Flyway V13 and the new plugin.
- Runtime materialization persisted 30 versioned sentiment observations under one dataset checksum.
- A queued one-candidate search completed with no failures.
- The worker emitted 30 `NEWS_SENTIMENT` signals, all BUY, and produced one trade.

### Added evaluator-driven Genetic Search

- Added a framework-neutral `CandidateFitnessSource` port.
- Upgraded Genetic Search to version 2.
- Persisted each population before requesting evaluated fitness.
- Kept persistence chunks within the requested batch size.
- Selected parents by evaluator score with candidate hash as deterministic tie break.
- Assigned failed and cancelled candidates the lowest fitness.
- Kept Random Search unchanged.
- Added tests proving that different fitness changes generation two but not the seed population.
- The final `mvn clean verify` gate passed.
- Runtime Genetic Search proposed 40 candidates across two generations, evaluated 32 unique genomes, and completed with no failures.

### Restored and audited the Java baseline

- Restored the committed Java 21 Maven reactor without touching the supplied requirements or reference images.
- Audited the written requirements, five reference screens, committed architecture, tests, and runtime topology.
- Confirmed that the existing ports, adapters, worker, outbox, inbox, provenance, observability, news, and sentiment foundations were usable.
- Found concrete MVP gaps: fixed strategy membership, one chart, closed-candle-only streaming, no win rate, missing chart overlays, and a dashboard that did not match the reference shell.

### Implemented composite subset discovery

- Random Search now varies strategy membership and parameter values.
- It emits every non-empty subset lazily and deterministically.
- Genetic crossover now matches genes by strategy type when parent memberships differ.
- Membership mutation never creates an empty strategy candidate.
- Core tests: 55 passed.
- Generator architecture tests: 2 passed.

### Implemented realtime open-candle updates and 1m support

- Added the normalized `CandleUpdate` domain value with a closed flag.
- Added the 1m timeframe.
- Binance mapping now accepts open and closed kline updates.
- Open updates reach the browser but do not enter durable history.
- The first closed update persists; duplicates remain suppressed.
- Core tests: 55 passed.
- Infrastructure tests: 20 passed.
- Focused API market tests: 3 passed.

### Implemented win rate end to end

- Added `winRatePct` to domain metrics, evaluator output, experiment details, provenance, leaderboard rows, and rerun comparison.
- Defined wins as trades with positive realized P/L. Breakeven is not a win. No trades returns zero.
- Added Flyway V9 with backfill and database constraints.
- Migration and persistence integration tests: 4 passed.
- Focused worker persistence test: 1 passed.

### Rebuilt the dashboard around the supplied reference

- Added the persistent light sidebar and separate Realtime, Strategy Engine, Discovery, Backtest, News Crawler, and Settings views.
- Added four independent chart states and STOMP subscriptions.
- Changing one timeframe reloads only that chart.
- Added candlesticks, price axes, time labels, volume, MA20, current signal, and connection state.
- Added strategy chips, per-strategy voting weights, and the five-stage discovery flow.
- Added win rate to the leaderboard.
- Added the Backtest view with result metrics, trade table, MA20, and entry or exit markers. Selecting a trade highlights it.
- Added sentiment counts and retained model version details on the News view.
- API reactor tests: 99 passed.
- JavaScript syntax and `git diff --check`: passed.
- Real container HTTP checks passed for the page, strategy catalog, system status, and all five timeframes.

### Corrected search lifecycle semantics

- Added `EVALUATING` as the state after candidate generation and before all jobs are terminal.
- Kept cancellation valid while the run evaluates outstanding work.
- Moved final completion to the durable worker transaction that closes the last active job.
- Handled the race where workers finish before generation performs its handoff.
- Lifecycle and coordinator unit tests: 7 passed.
- Search API tests: 4 passed.
- PostgreSQL search lifecycle tests: 2 passed.
- Focused PostgreSQL and RabbitMQ worker lifecycle test: 1 passed.

### Verification status

- The final `mvn clean verify` gate passed across all six Maven modules after the lifecycle correction.
- `scripts/verify-architecture-proofs.sh` passed with Docker access, including Binance recovery, News isolation, MACD extension, generator replacement, worker scaling, and Top #1 provenance.
- The local API, PostgreSQL, RabbitMQ, and worker containers start successfully.
- A real runtime search generated 8 candidates, completed all 8 with no failures, and exposed 8 leaderboard rows plus experiment details through public APIs.
- Runtime leaderboard membership included `MA+RSI`, `BB+MA`, `BB+RSI+SR`, and `MA+RSI+SR`.
- Rebuilt the API and worker images after the lifecycle correction.
- A final 60-candidate runtime search reported `RUNNING`, `EVALUATING`, then `COMPLETED`.
- All 60 final-image jobs completed with no failures. The top result exposed two trades and the same 100% win rate through leaderboard and experiment detail APIs.
- Confirmed fault isolation in the final stack: CryptoCompare returns HTTP 401 without `NEWS_API_KEY`, while Market Data, Sentiment, Queue, API, and Worker stay up.
- Browser visual inspection is still pending because this session has no connected browser.

### Added Gemini strategy authoring

- Added prompt drafts that show a plain-language Gemini idea before confirmation.
- Restricted generated output to JSON that composes registered strategy plugins and policies.
- Added two repair attempts after an invalid first response.
- Added a deterministic 250-candle smoke test before storage.
- Stored immutable, name-versioned strategies under the signed-in account, with list, detail, and delete APIs.
- Added Flyway V15 and left `GEMINI_API_KEY` blank for local configuration.
- Added a core test for confirmation, repair, validation, smoke execution, and account ownership.

### Added continuous discovery schedules

- Added account-owned schedules with pair, timeframe, lookback, capital, candidate limit, and interval.
- Defaulted the loop interval to 24 hours and forced every scheduled run to use Genetic Search.
- Added database compare-and-set claiming so one schedule cannot overlap itself.
- Added start, stop, terminal-run tracking, completed count, next run time, and last error.
- Added startup recovery for an API process that stopped during a run.
- Added Flyway V16, scheduler service tests, and a PostgreSQL claim and recovery test.
- The final `mvn clean verify` gate passed with all 16 migrations.
- `scripts/verify-architecture-proofs.sh` passed with PostgreSQL and RabbitMQ Testcontainers.
