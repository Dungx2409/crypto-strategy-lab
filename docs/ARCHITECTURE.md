# Crypto Strategy Lab architecture

## Scope

The written requirements define the product scope. The five supplied images guide layout and visual style. Prompt-based strategy authoring uses Gemini and restricted JSON. Article-link authoring and self-healing LLM news extraction remain future work.

The platform supports research and coursework. It never places real orders.

## Architectural drivers

The design responds to eight concrete pressures from the requirements:

1. Add a strategy such as MACD without changing the backtester, evaluator, ranking service, or UI contract.
2. Replace Binance without exposing provider payloads to the browser or domain.
3. Replace Random Search with another generator without rewriting the experiment pipeline.
4. Grow from hundreds to many thousands of backtests by adding workers.
5. Recover market streams without losing or duplicating closed candles.
6. Keep the market path usable when news or sentiment fails.
7. Show search, queue, worker, and provider state while the system runs.
8. Reproduce a leaderboard result from exact data, strategy, configuration, engine, evaluator, model, code, and build versions.

## System structure

```text
Browser
  | REST and STOMP
  v
api-app
  | application ports
  v
core <------- infrastructure adapters
  ^                    |
  |                    | PostgreSQL, RabbitMQ, Binance, CryptoCompare
worker-app ------------+
```

The Maven dependency direction is:

```text
api-app / worker-app -> infrastructure -> core
integration-tests    -> runnable modules in test scope
```

`core` contains domain values, application services, and ports. It has no Spring MVC, AMQP, JDBC, or provider DTO dependency. `infrastructure` implements ports. `api-app` owns REST, STOMP, health endpoints, and the browser application. `worker-app` consumes backtest jobs and can scale independently.

## Decisions

### AD-01: modular monolith with a separate worker process

Context: Most business capabilities change together and fit one course project, while backtest execution needs independent scaling.

Decision: Keep domain and adapters in one Maven reactor. Run the API and backtest worker as separate Spring Boot applications.

Why: A full microservice split would add distributed configuration, service discovery, and more network failure modes without a matching requirement. A single process would prevent independent worker scaling.

Alternatives: One deployable monolith; one service per bounded context.

Consequences: Modules share a repository and release version. Backtest capacity can still scale by changing the worker replica count. PostgreSQL and RabbitMQ remain shared infrastructure and must be monitored for contention.

Evidence: Maven Enforcer rules, `ArchitectureRulesTest`, `WorkerScalingArchitectureTest`, Docker Compose API and worker services.

### AD-02: ports and adapters around the domain

Context: Market providers, persistence, messaging, news sources, sentiment models, and search generators must be replaceable.

Decision: Domain and application code depend on interfaces in `core`. Provider, JDBC, RabbitMQ, and Spring code implement those interfaces in outer modules.

Why: Dependency inversion keeps business rules independent from transport and storage details. It also gives tests small fake ports instead of requiring full infrastructure.

Alternatives: Direct JDBC and HTTP calls from strategies; a shared God Service.

Consequences: There are more interfaces and wiring classes. The extra types pay for themselves because every required replacement scenario crosses one of these boundaries.

Evidence: ArchUnit rules and package-level Maven dependency checks.

### AD-03: normalized candle and news contracts

Context: The browser and strategies must not speak Binance or CryptoCompare JSON.

Decision: Adapters map external payloads to `Candle`, `CandleUpdate`, and `NewsItem` before data enters application services.

Why: Provider fields and schema changes stay inside one adapter. Every consumer sees one stable vocabulary.

Alternatives: Forward provider JSON through controllers; duplicate mapping in each consumer.

Consequences: Adapters own explicit mapping and validation. Adding a provider requires a mapper and port implementation, not browser changes.

Evidence: `BinancePayloadMapperTest`, `CryptoCompareNewsProviderTest`, and ArchUnit no-leak rules.

### AD-04: realtime candle lifecycle uses upsert semantics

Context: A live candle changes many times before it closes. Closed candles must remain durable and duplicate-safe.

Decision: `CandleUpdate` carries a normalized candle plus a `closed` flag. The stream service broadcasts every update. It persists and measures only the first closed version. STOMP sends `CANDLE_UPDATE`, and browser charts upsert by `openTime`.

Why: The browser can replace the current candle and append the next one without knowing Binance field names. Persistence still stores stable closed candles once.

Alternatives: Publish only closed candles; persist every tick; expose Binance's `x` field.

Consequences: Realtime display and durable history have different write policies. `openTime` is the idempotency key. Gap recovery continues to publish closed normalized candles.

Evidence: `MarketDataStreamServiceTest`, `BinancePayloadMapperTest`, `StompCandleUpdatePublisherTest`, and `MarketDashboardIsolationTest`.

### AD-05: each chart owns its subscription and state

Context: The UI must show four timeframes and changing one chart must not reload the page or the other charts.

Decision: The browser keeps four `chartStates`. Each state owns a timeframe, candle list, request version, canvas, and STOMP subscription ID.

Why: A global selected timeframe cannot isolate updates. Per-chart state maps directly to the requirement and keeps reload work local.

Alternatives: Four page reloads; one shared timeframe; polling every chart.

Consequences: The browser opens four logical STOMP subscriptions over one WebSocket. The backend subscription tracker reference-counts provider streams. Chart zero is the immutable dataset source for a search run. The shared `Timeframe` enum now defines 1m, 5m, 15m, 30m, 1h, 2h, 4h, and 1d, so REST, STOMP, Binance, persistence, and the browser use the same codes.

Evidence: `DomainContractTest`, `ReferenceDashboardTest`, `MarketDashboardIsolationTest`, and runtime HTTP checks for supported timeframe codes.

### AD-06: strategy plugins use registry and factory contracts

Context: New strategies must not create switch statements in controllers or experiment services.

Decision: Each strategy has a versioned factory. `StrategyRegistry` discovers factories and exposes parameter schemas through the API.

Why: Creation and validation belong to the plugin. Consumers work with the `Strategy` contract and normalized signals.

Alternatives: A strategy type enum with a central switch; runtime script execution.

Consequences: Adding a Java plugin requires its strategy and factory bean. No database column or downstream branch is needed because configuration is stored as versioned JSON.

Evidence: `StrategyExtensionArchitectureTest`, baseline strategy tests, MACD tests, and catalog controller tests.

### AD-07: combination policy is separate from strategy analysis

Context: MA, RSI, Bollinger Bands, and Support or Resistance can disagree.

Decision: Strategies emit normalized signals. `MajorityVotePolicy` and `WeightedVotePolicy` combine them after analysis.

Why: Indicator logic and conflict resolution change for different reasons. Keeping them separate follows single responsibility and lets one candidate reuse the same strategy plugins with another policy.

Alternatives: Each composite strategy hardcodes voting; strategies call one another.

Consequences: Candidate identity includes policy type, version, threshold, and weights.

Evidence: `CombinationPoliciesTest` and candidate canonicalization tests.

### AD-08: search varies membership as well as parameters

Context: The main experiment question compares combinations such as MA plus RSI against Bollinger Bands plus Support or Resistance. Varying only parameters of one fixed full set does not answer it.

Decision: Random generation models each selected family as excluded or one valid parameter configuration, then removes the all-excluded case. Genetic crossover matches genes by strategy type, and membership mutation may remove a member but never the final member.

Why: Strategy membership is part of the search space. This makes the generator produce simple and composite candidates through the same contract.

Alternatives: Fixed membership with parameter variation; materialize every candidate before search.

Consequences: The search space grows by the product of each family's parameter choices plus the excluded choice. Generation stays lazy, deterministic, and bounded by stop conditions.

Evidence: `RandomStrategyGeneratorTest` and `GeneratorReplacementArchitectureTest`.

### AD-09: deterministic backtest, evaluation, and ranking are separate stages

Context: Strategy logic, trade simulation, metric calculation, and rank ordering need independent tests and versions.

Decision: The pipeline is Candidate, Backtest, Evaluate, Rank. A signal from candle N executes at candle N plus one open. The engine only exposes candle prefixes to strategies.

Why: This blocks look-ahead bias and keeps score changes out of the execution engine.

Alternatives: Let strategies simulate their own trades; evaluate inside the backtest loop.

Consequences: Engine, evaluator, and ranking versions become provenance fields. A final open position closes deterministically at the last candle close.

Evidence: `DeterministicBacktestEngineTest`, `ExperimentPipelineServiceTest`, and `EvaluationRankingStateTest`.

### AD-10: win rate is a stored evaluation metric

Context: The MVP requires Total Return, Win Rate, Maximum Drawdown, and Number of Trades.

Decision: Win rate equals closed trades with `pnl > 0`, divided by all closed trades, times 100. No trades returns zero. Breakeven trades are not wins. Flyway V9 stores the value in evaluation and leaderboard tables.

Why: Recomputing from trades on every leaderboard read would mix projection logic with metric definition. Storing the evaluator output also preserves historical meaning.

Alternatives: Derive win rate only in the browser; count breakeven as a win.

Consequences: Evaluator version changes if the formula changes. Database constraints keep the value between zero and 100.

Evidence: `EvaluationRankingStateTest`, `ExperimentControllerTest`, `AsyncEvaluationRankingIT`, `ExperimentPipelineIT`, and `PostgresqlMigrationIT`.

### AD-11: durable queue with transactional outbox and idempotent inbox

Context: Backtests can outlive an API request. Broker delivery is at least once, and workers may crash or scale horizontally.

Decision: PostgreSQL records dispatch intent and domain events in outbox tables. Relays publish with broker confirmation. Workers claim jobs with leases, write completion and events transactionally, and consumers deduplicate event IDs.

Why: Direct publish after a database commit can lose jobs. Direct database writes after message acknowledgment can lose results. Idempotency is required when RabbitMQ redelivers.

Alternatives: Synchronous in-process execution; fire-and-forget broker publishing.

Consequences: The system contains retry, lease, outbox, and inbox state. In return, source code does not change when worker replicas increase.

Evidence: `BacktestJobOutboxIT`, `BacktestWorkerIT`, async projection tests, and messaging listener tests.

### AD-12: leaderboard is a query projection

Context: Ranking reads are frequent and should not rebuild every experiment aggregate on each request.

Decision: Completion events update an idempotent leaderboard table keyed by experiment. REST and STOMP read that projection.

Why: The write path keeps immutable experiment artifacts, while the query path returns Top K rows quickly.

Alternatives: Recalculate all rankings per request; use full event sourcing.

Consequences: Projection consumers must handle duplicates and preserve deterministic tie breaking. Full event sourcing is not used because replay of every state transition is not required.

Evidence: `AsyncEvaluationRankingIT`, ranking tests, and realtime publisher tests.

### AD-13: immutable provenance is part of the result

Context: A top result is useless for coursework if nobody can identify its strategy, dataset, engine, evaluator, model, and code versions.

Decision: Experiments store candidate hash, strategy versions and parameters, policy, dataset checksum and range, execution configuration, generator snapshot, evaluator version, code commit, build version, signals, trades, and metrics.

Why: Reproducibility is a core quality attribute, not an audit log added later.

Alternatives: Store only leaderboard values; overwrite strategy configuration in place.

Consequences: Experiment rows and artifacts use more storage. Old results remain interpretable after plugins or formulas change.

Evidence: `ExperimentPipelineIT`, provenance controller tests, and the rerun path.

### AD-14: news and sentiment fail independently

Context: News providers and models fail for reasons unrelated to the market stream.

Decision: `NewsCollector` depends on separate `NewsProvider`, `SentimentAnalyzer`, `NewsStore`, and telemetry ports. Its scheduler, health status, failures, and timeouts do not control market services.

Why: A provider outage must degrade only the News screen.

Alternatives: Put collection, sentiment, and market data in one scheduled service.

Consequences: Stored news remains readable during provider failure. Health can show Market UP and News DOWN at the same time.

Evidence: `NewsFailureIsolationTest`, `NewsCollectorTest`, and `NewsSentimentIT`.

### AD-15: one browser application with route-like views

Context: The reference uses a shared sidebar and separate Realtime, Strategy, Discovery, Backtest, News, and Settings screens. The project has one team and one release.

Decision: Serve a small static browser application from `api-app`. `app.js` owns view navigation. Market, experiment, and news scripts own their feature state and call only backend contracts.

Why: Micro-frontends would add build and deployment work with no independent teams. Server-rendering every market update would fight the realtime requirement.

Alternatives: One long page; React micro-frontends; Java desktop UI.

Consequences: Navigation needs no page reload. Static resource tests protect required screens and backend-only data use. Browser rendering still needs a real browser check before a presentation.

Evidence: `ReferenceDashboardTest`, `M7DashboardTest`, `MarketDashboardIsolationTest`, and `NewsDashboardTest`.

### AD-16: generation and run completion are separate states

Context: Candidate generation can stop while jobs are pending, queued, or running. Calling the run complete at that point hides active work and disables cancellation too early.

Decision: Candidate generation changes a run from `RUNNING` to `EVALUATING`. The durable worker transaction for a completed, failed, or cancelled job checks whether every job is terminal. The final terminal job changes the run to `COMPLETED`. The generation handoff also handles the race where fast workers drain all jobs first.

Why: Status must describe the whole search loop, not only its producer. The worker repository already owns atomic job transitions and can check remaining work in the same transaction.

Alternatives: Keep `COMPLETED` and add a browser-only warning; poll and repair status in a scheduler; add another orchestration service.

Consequences: `EVALUATING` is nonterminal and cancellable. No database migration is needed because status is stored as text. Every terminal worker path must run the same completion check.

Evidence: `SearchCoordinatorTest`, `SearchControlPoliciesTest`, `SearchRunControllerTest`, `SearchRunIT`, and `BacktestWorkerIT`.

### AD-17: Long and Short share one versioned portfolio state machine

Context: The extension requirements include Long and Short trading. The original engine rejected `allowShort`, and trades did not record direction.

Decision: Engine version 2 opens Long on BUY and Short on SELL when shorting is enabled. The opposite signal closes the position at the next candle open. Dataset-end liquidation closes either direction. `TradeDirection` is stored on every trade, and Flyway V10 backfills old rows as `LONG`.

Why: Direction belongs to the trade artifact and portfolio rules, not to strategy plugins or the browser. One state machine keeps fill timing, fees, and no-look-ahead rules identical for both directions.

Alternatives: Model shorts as negative quantity; create a second backtest engine; infer direction from entry and exit prices.

Consequences: Short positions use starting cash as collateral. Net P/L is entry minus exit for Short and exit minus entry for Long, less entry and exit fees. Version 1 remains accepted for old long-only reruns. The capabilities endpoint tells the browser which engine version and fill policy to send.

Evidence: `DeterministicBacktestEngineTest`, `ExperimentControllerTest`, `ExperimentPipelineIT`, `PostgresqlMigrationIT`, and the full Maven verification gate.

### AD-18: Position sizing is part of execution configuration

Context: The trading extensions include Position Sizing. Candidate strategy parameters should not decide how much account capital the execution engine commits.

Decision: Engine version 3 adds `positionSizePct` to `ExecutionConfig`. Each new position commits that percentage of currently available cash. Uncommitted cash remains in the portfolio and equity curve.

Why: Position size is an execution and risk rule. Keeping it in the immutable execution snapshot lets the same strategy run under different risk settings without changing plugin identity.

Alternatives: Put size in every strategy; always commit all cash; store a quantity directly in candidate parameters.

Consequences: Missing values default to 100% for old JSON. Values must be greater than zero and no more than 100. Engines 1 and 2 reject non-default sizing, so historical semantics remain fixed. The browser reads engine version and fill policy from backend capabilities and sends the chosen percentage with the search request.

Evidence: `DeterministicBacktestEngineTest`, `SearchRunControllerTest`, provenance persistence tests, and `mvn clean verify`.

### AD-19: Risk exits use conservative OHLC ordering

Context: The trading extensions include Stop Loss and Take Profit, but candle data does not reveal the order of intrabar high and low prices.

Decision: Engine version 4 checks risk exits after a next-open entry and before analyzing the candle close. Stop Loss is checked before Take Profit. If both thresholds fall inside one candle, Stop Loss wins. A gap beyond a threshold fills at the candle open; otherwise the threshold price is used. The trade stores `STOP_LOSS`, `TAKE_PROFIT`, `SIGNAL`, or `DATASET_END` as its exit reason.

Why: Choosing the favorable target when both levels are touched would overstate results. A fixed conservative rule makes the result reproducible and easy to explain.

Alternatives: Take Profit first; infer an intrabar path; use lower-timeframe candles without declaring that new dataset dependency.

Consequences: Stop and target percentages are optional immutable execution settings. Engines 1 through 3 reject them. Flyway V11 backfills old trades with `SIGNAL`. Both JDBC paths, REST results, provenance, and the Backtest table preserve the reason.

Evidence: Risk-ordering tests in `DeterministicBacktestEngineTest`, API response tests, `PostgresqlMigrationIT`, worker integration tests, and `mvn clean verify`.

### AD-20: Trailing Stop advances only after a completed candle

Context: A trailing stop needs a high-water mark for Long or a low-water mark for Short. OHLC data cannot prove whether the candle extreme occurred before its opposite extreme.

Decision: Engine version 5 checks the current candle against the trailing level derived from earlier completed candles. If the position remains open, the engine updates the water mark from the current candle for use on the next candle. Fixed Stop Loss is checked first, Trailing Stop second, and Take Profit third.

Why: Updating and triggering from the same candle would assume an intrabar order that the dataset does not contain. Using completed-candle marks is conservative and reproducible.

Alternatives: Assume high before low; use tick data; update at each candle close without allowing the next candle to gap through the level.

Consequences: Gaps fill at the new candle open. Normal trailing exits fill at the prior trailing level. Trades store `TRAILING_STOP`, and Flyway V12 expands the existing exit-reason constraint. Engines 1 through 4 reject trailing configuration.

Evidence: The completed-water-mark test in `DeterministicBacktestEngineTest`, migration tests, worker persistence tests, and `mvn clean verify`.

### AD-21: Supported market pairs are an application allow-list

Context: The market extension includes multiple coins. Provider and domain contracts already accept normalized trading pairs, but the API allow-list contained only BTCUSDT.

Decision: `CRYPTO_MARKET_SUPPORTED_SYMBOLS` configures the API allow-list. The default contains BTCUSDT, ETHUSDT, SOLUSDT, and BNBUSDT. Every chart uses the same selected pair while keeping its own timeframe and subscription.

Why: An allow-list prevents the API from proxying arbitrary provider symbols while avoiding code changes for each supported coin. Pair validation remains in the application service, outside Binance mapping and browser logic.

Alternatives: Accept every string; hardcode one controller branch per coin; query the exchange catalog on every request.

Consequences: Operators can add another valid Binance pair through environment configuration. The browser still needs a listed option before users can select it. Changing pair reloads all four charts because pair is a page-level control; changing timeframe reloads only one chart.

Evidence: `ReferenceDashboardTest`, application configuration, Docker environment wiring, and live ETHUSDT, SOLUSDT, and BNBUSDT market API responses.

### AD-22: Sentiment is copied into the immutable experiment dataset

Context: `NewsSentimentStrategy` must join search combinations, but reading the current news database during a historical backtest would produce different results later and leak future news into earlier candles.

Decision: A normalized `SentimentObservation` stores source ID, observation time, score, model identity, input version, and preprocessing version. Dataset materialization sorts these observations, includes them in the SHA-256 checksum, and persists them beside candles through Flyway V13. The backtester gives each `StrategyContext` only observations published by that candle close.

Why: Sentiment is an experiment input, not a live service dependency. Copying it into the dataset makes model and data versions part of immutable provenance and gives the no-look-ahead rule one enforcement point.

Alternatives: Let the strategy query `NewsStore`; store only an average score; apply today's sentiment to every historical candle.

Consequences: Candle-only datasets keep their old checksum because an empty sentiment list does not change canonical bytes. Sentiment-aware datasets have a different checksum. `NEWS_SENTIMENT@1.0` averages observations inside a configurable window and returns BUY, SELL, or HOLD from configurable thresholds. The browser copies its currently stored, versioned news predictions into the dataset request.

Evidence: Dataset checksum tests, `MarketDatasetMaterializationIT`, the strategy time-window test, the backtester future-observation test, factory test, migration test, and `mvn clean verify`.

### AD-23: Genetic generations wait for durable candidate fitness

Context: Genetic version 1 selected parents by candidate hash. It crossed and mutated genomes but did not use backtest results, so it was not evaluator-driven search.

Decision: Genetic version 2 requests candidate fitness through `CandidateFitnessSource`. The coordinator persists one complete population, in chunks no larger than the requested batch size, before asking for the next generation. The JDBC repository waits on existing job and metric tables. Completed candidates use evaluator score; failed or cancelled candidates receive the lowest fitness. Parent sorting uses score first and candidate hash only as a deterministic tie break.

Why: The generator needs evaluated outcomes but must not depend on JDBC, RabbitMQ, the worker, or the concrete evaluator. A small port preserves dependency direction. Population boundaries prevent the generator from waiting on candidates that have not been persisted yet.

Alternatives: Keep hash ordering; inject the evaluator into the generator; materialize every generation and run it synchronously inside the API process.

Consequences: Genetic Search pauses between generations while workers drain the current population. Random Search still uses the original streaming path. The population is up to 20 candidates and can span several persistence chunks. Cancellation ends the wait. Duplicate child genomes may reduce the number of unique persisted candidates, while generated count records all proposals.

Evidence: Fitness-ordering unit tests, generator replacement architecture tests, the full Maven suite, and a runtime two-generation search with generator version 2 provenance.

### AD-24: One market provider is selected at application startup

Context: The extension asks whether Binance can become Binance plus OKX without changing the frontend. Binance and OKX use different symbols, timeframe codes, historical response shapes, and WebSocket subscription protocols.

Decision: `BinanceMarketDataProvider` and `OkxMarketDataProvider` both implement the existing core port. `CRYPTO_MARKET_PROVIDER=binance|okx` selects exactly one adapter when the API starts. Each adapter owns its provider DTOs, symbol and interval mapping, pagination, HTTP transport, and WebSocket protocol. Both emit the same `Candle` and `CandleUpdate` domain values.

Why: Controllers, streaming services, persistence, strategies, and browser code should depend on the stable market contract. Provider-specific fields belong at the system edge.

Alternatives: Expose provider JSON to the browser; add provider branches to `MarketDataService`; query both exchanges and merge their candles.

Consequences: Changing provider requires an API restart. This is replacement, not automatic failover or multi-exchange aggregation. The OKX adapter currently accepts the supported USDT pairs and converts symbols such as `BTCUSDT` to `BTC-USDT`. Binance remains the default.

Evidence: Provider selection tests, OKX mapper and provider tests, the generalized provider DTO ArchUnit rule, the full Maven gate, and a runtime call that returned normalized OKX candles through the unchanged market REST endpoint.

### AD-25: Accounts use server-side sessions and BCrypt credentials

Context: User-created strategies and continuous discovery schedules need an owner. The browser and API share one origin, so bearer tokens would add client-side token handling without solving another requirement.

Decision: Accounts use a normalized, case-insensitive username and a BCrypt password hash with cost 12. Successful registration or login creates an HTTP-only, SameSite Strict server session. The session stores only account ID and username. Public market and leaderboard APIs remain public. Account-owned APIs call one session identity guard.

Why: The browser never reads a credential or bearer token after login. SameSite Strict blocks cross-site cookie submission, and JSON mutation requests are not accepted as cross-site HTML forms. Invalid login returns one generic message, so it does not reveal whether a username exists.

Alternatives: JWT bearer tokens in browser storage; OAuth login; email identity with verification.

Consequences: Sessions live in API memory and end when that API instance restarts. Horizontal API scaling would require sticky sessions or a shared session store. `SESSION_COOKIE_SECURE` must be true behind HTTPS. Username recovery, email verification, and password reset are outside the current requirement.

Evidence: Account service and controller tests, BCrypt hash test, Flyway V14 migration test, and PostgreSQL repository integration test.

### AD-26: Gemini writes restricted strategy JSON, never executable code

Context: A signed-in user must describe a strategy in natural language, review the idea, and add it without rebuilding the Java application. Running generated Java would allow file, process, reflection, and network access inside the API.

Decision: Gemini first returns a plain-language idea. Only an explicit confirmation asks Gemini for JSON. The JSON may contain a name, description, registered strategy definitions, and a registered combination policy. The application decodes it, creates every plugin and policy through the existing registries, and runs each strategy against 250 fixed candles. It requests a repaired JSON document after a decode, validation, or smoke-test failure, with a total limit of three responses. Accepted documents are stored per account with a name-based version number.

Why: JSON can compose existing tested plugins without introducing executable code. The confirmation step prevents an initial prompt from becoming a saved strategy without review. The fixed smoke input finds invalid parameters and runtime failures before storage.

Alternatives: Compile generated Java; interpret a general scripting language; save the first model response without confirmation.

Consequences: Users can create new configurations and combinations, but they cannot invent a new indicator algorithm outside the registered plugin set. `GEMINI_API_KEY` is blank by default. The API starts without it and reports that authoring is unavailable until an operator supplies the key. Strategy versions remain immutable rows and can be deleted only by their owning account.

Evidence: `StrategyAuthoringServiceTest`, Flyway V15, account ownership in `JdbcUserStrategyRepository`, and the full Maven verification gate.

### AD-27: Continuous discovery uses persisted account-owned schedules

Context: Genetic discovery must run repeatedly for 24-hour operation, survive API restarts, allow user stop and start controls, and never overlap two runs for one schedule.

Decision: `discovery_schedules` stores the pair, timeframe, lookback, capital, candidate limit, interval, status, next run time, active search UUID, completed count, and last error. A database compare-and-set claim reserves a due schedule before creating its Genetic Search run. Polling checks the active search state and clears the claim only after a terminal result. Startup recovery clears claims left by a stopped API process and makes them due again.

Why: An in-memory timer would lose ownership and timing state after restart. The database claim prevents two scheduler ticks from launching the same schedule. Reusing Genetic Search keeps the loop bounded by candidate count instead of exhaustive enumeration.

Alternatives: A permanent in-memory loop per user; cron entries without database state; exhaustive strategy enumeration.

Consequences: The default interval is 24 hours, but the API accepts any interval of at least one minute. A stopped schedule cancels its active search. Restart recovery may leave the interrupted `search_runs` row for diagnosis, then starts a new run with a new UUID. The schedule reservation UUID has no foreign key because the claim happens before the search row is created.

Evidence: `ContinuousDiscoveryServiceTest`, `DiscoveryScheduleRepositoryIT`, Flyway V16, and the full Maven verification gate.

## Runtime flows

### Realtime flow

```text
Binance or OKX WebSocket
  -> selected market adapter
  -> CandleUpdate
  -> MarketDataStreamService
  -> STOMP CANDLE_UPDATE
  -> matching chart state
  -> replace by openTime or append
```

Closed updates also pass through `CandleStore.saveIfAbsent`. Open updates do not enter durable candle history.

### Search and result flow

```text
Browser materializes exact chart candles
  -> checksummed MarketDataset
  -> SearchCoordinator
  -> StrategyGenerator
  -> outbox
  -> RabbitMQ backtest queue
  -> worker
  -> BacktestCompleted
  -> evaluator
  -> StrategyEvaluated
  -> ranking projection
  -> leaderboard REST and STOMP
```

### News flow

```text
NewsProvider -> NewsCollector -> NewsStore
                         |
                         -> SentimentAnalyzer -> versioned sentiment result
```

## Known limits

- Browser visual inspection could not run in the current session because no browser connection was available. HTTP and source tests ran against the real container.
- Market provider selection happens at API startup. The system does not automatically fail over between Binance and OKX or combine their candles.
- Natural-language authoring can only compose registered plugins. It does not generate new Java indicator implementations.
- Gemini authoring cannot run until `GEMINI_API_KEY` is supplied.
- LLM-assisted crawler repair is not implemented.
