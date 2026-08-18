# ADR-005: Durable Queue and Independent Workers

## Context

Backtests are parallel, potentially long-running, and scale differently from API
traffic.

## Decision

Use RabbitMQ durable jobs and independently runnable stateless worker replicas.
Workers commit durable state before acknowledging messages.

## Alternatives

Run every backtest synchronously in the API; introduce separate microservices
for every bounded context; use Kafka.

## Consequences

At-least-once delivery requires idempotency, retries, DLQ, and observability.
Worker count can change without core code changes.

## Evidence

M5.2 declares durable job/DLQ topology and implements transactional dispatch
outbox publication with correlated RabbitMQ confirms. Persisted jobs remain
`PENDING_DISPATCH` until confirmation; confirmed publication atomically moves
the job and experiment to `QUEUED`.

M5.3 adds the independently runnable consumer with manual acknowledgment,
atomic claim/lease, `experimentId` idempotency, three delayed retries, and DLQ
routing. Signals, trades, metrics, `COMPLETED`, and the `BacktestCompleted`
outbox row commit atomically before ACK. PostgreSQL/RabbitMQ integration tests
prove duplicate delivery produces only one result and one concurrent worker owns
the claim.

M5.5 adds a Compose `worker` service without fixed container identity or host
port and exposes active-worker, processed-job, failure, and queue-depth meters.
The scaling integration test runs one and then three independent consumers on
the same queue under a fixed synthetic delay. Three consumers overlap work and
drain the workload faster, while database assertions still find exactly one
completed experiment, metric row, execution attempt, and completion event per
unique `experimentId`, including duplicated deliveries.
