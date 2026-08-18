# M7 Architecture Proof Matrix

This matrix maps each final demonstration to repeatable evidence. The tests use
the same core/application contracts and PostgreSQL/RabbitMQ adapters as the
runnable applications; no dashboard fixture is treated as production evidence.

| Proof | Automated evidence | Invariant demonstrated |
|---|---|---|
| Add MACD | Production `MacdStrategy` + `MacdStrategyFactory`; `MacdStrategyTest`, `MacdStrategyFactoryTest`, `StrategyExtensionArchitectureTest`, `ArchitectureRulesTest` | `MACD@1.0` is discovered at runtime without changes to Backtester, Evaluator, Ranking, controllers, or schema |
| Random → Genetic | `GeneratorReplacementArchitectureTest`, `SearchCoordinatorTest` | Both generators produce the same candidate contract and can be chosen per persisted run; downstream pipeline is generator-agnostic |
| Worker 1 → 3 | `BacktestWorkerIT`, `WorkerScalingArchitectureTest` | The same worker code/image and queue run at either replica count; atomic claim and idempotency prevent duplicate completion |
| News failure | `NewsFailureIsolationTest`, `NewsCollectorTest` | Provider/model failure changes only News/Sentiment health and does not enter Market, Search, Backtest, or Leaderboard dependencies |
| Binance disconnect | `MarketDataStreamServiceTest`, `CandleStoreIT` | Reconnect performs boundary-inclusive gap recovery; repeated candles are harmless in service and PostgreSQL |
| Top #1 provenance | `ExperimentPipelineIT` | The first leaderboard row links by `experimentId` to candidate, strategy/policy, immutable dataset/checksum, execution/generator/evaluator, code/build, signals, trades, and metrics |

Run the focused proof suite:

```bash
./scripts/verify-architecture-proofs.sh
```

Run the full verification gate:

```bash
mvn clean verify
```

Run the deployable topology with one worker, then three workers:

```bash
docker compose up --build
docker compose up --build --scale worker=3
```

Replica scaling is a deployment change only. The `worker` service has neither a
fixed container name nor a published host port, and all replicas consume the
same durable queue.

## Manual demonstration path

1. Open `http://localhost:8080` and wait for the Market panel to receive a
   backend candle snapshot.
2. Select strategy parameters, combination policy, `random` or `genetic`, stop
   conditions, then start a search. The dashboard first persists the exact
   candle snapshot through `POST /api/v1/datasets`.
3. Observe generated/queued/completed/failed counters and the isolated progress
   WebSocket topic.
4. Select leaderboard rank #1. The details panel resolves the row's
   `experimentId` and fetches both artifacts and immutable provenance.
5. Inspect System Status and the independent News panel. A News provider failure
   degrades News only; stored data and the other modules remain usable.
