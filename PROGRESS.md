# Progress

## 2026-08-21

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
- Browser visual inspection is still pending because this session has no connected browser.
