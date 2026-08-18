# Domain Event Catalog

All events use `DomainEventEnvelope<T>`. Consumers assume at-least-once
delivery, never global ordering, and must make processing idempotent.

| Event | Owner | Schema | Ordering key | Duplicate handling | Consumer failure | Replay policy |
|---|---|---:|---|---|---|---|
| `MarketPriceUpdated` | Market Data | 1 | `symbol:timeframe` | Upsert/ignore by candle identity | Bounded retry; degrade realtime health | Replay only when a consumer explicitly supports transient prices |
| `CandleClosed` | Market Data | 1 | `symbol:timeframe` | Unique `(symbol,timeframe,openTime)` | Retry, then observable failure | Safe to replay from persisted candles |
| `StrategyGenerated` | Strategy/Search | 1 | `searchRunId` | Unique candidate hash per search | Retry persistence; reject invalid config | Replay from candidate snapshot |
| `BacktestStarted` | Experiment | 1 | `experimentId` | Atomic state transition | Retry transient storage failure | Rebuild from experiment state, not side effects |
| `BacktestCompleted` | Experiment | 1 | `experimentId` | Inbox `eventId`; experiment completion unique | Bounded retry; DLQ on exhaustion | Safe from immutable result/outbox |
| `StrategyEvaluated` | Experiment/Evaluation | 1 | `experimentId` | One evaluation per experiment plus inbox | Bounded retry; DLQ on exhaustion | Safe from persisted evaluation |
| `LeaderboardUpdated` | Experiment/Ranking | 1 | `searchRunId` | Idempotent projection keyed by experiment | Retry; rebuild projection if required | Rebuild from evaluations |
| `NewsCollected` | News Intelligence | 1 | `newsId` | Upsert by `newsId` | Bounded retry; News health degrades | Replay normalized stored news |
| `SentimentAnalyzed` | News Intelligence | 1 | `newsId` | Unique prediction identity/model metadata | Bounded retry; Sentiment health degrades | Replay stored prediction or re-run with explicit model version |

## Envelope fields

`eventId`, `eventType`, `schemaVersion`, `occurredAt`, `aggregateType`,
`aggregateId`, `correlationId`, `causationId`, `orderingKey`, and `payload` are
part of the stable core contract. Schema evolution must remain backward
compatible or introduce a new schema version with migration guidance.

M5 implements the `BacktestJob` command channel, transactional dispatch/retry
outboxes, and transactional production/publication of `BacktestCompleted`.
`BacktestJobDispatchRequested` and `BacktestJobRetryRequested` are internal
outbox classifications, not additional domain events. Idempotent consumers use
`processed_events` for `BacktestCompleted`, `StrategyEvaluated`, and
`LeaderboardUpdated`. M5.5 also fans the latter two events into isolated search
progress and leaderboard STOMP topics; an event already recorded for a realtime
consumer is acknowledged without another UI publication.
