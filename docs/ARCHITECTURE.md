# Crypto Strategy Lab architecture

## Scope

The written requirements define the product scope. The five supplied images guide layout and visual style. Two image-only ideas are outside the current build: prompt or URL strategy generation with an LLM, and self-healing LLM news extraction. They remain possible extensions, but the current system does not pretend to provide them.

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

Consequences: The browser opens four logical STOMP subscriptions over one WebSocket. The backend subscription tracker reference-counts provider streams. Chart zero is the immutable dataset source for a search run.

Evidence: `ReferenceDashboardTest`, `MarketDashboardIsolationTest`, and runtime HTTP checks for 1m, 5m, 15m, 1h, and 4h.

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

## Runtime flows

### Realtime flow

```text
Binance WebSocket
  -> Binance adapter
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

- The current genetic generator proves replaceability and membership crossover, but it does not yet feed evaluated fitness back into parent selection.
- Browser visual inspection could not run in the current session because no browser connection was available. HTTP and source tests ran against the real container.
- Prompt or URL strategy authoring and self-healing LLM news extraction are not part of the written MVP and are not implemented.
