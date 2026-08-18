# ADR-010: Mandatory Stop Conditions and Loop Observability

## Context

Continuous search can consume unbounded CPU, memory, database, and queue capacity
unless termination and progress are explicit.

## Decision

Every search has at least one automatic stop condition plus user cancellation.
Generation is streamed/batched and loop/queue/job/failure state is observable.

## Alternatives

An unbounded `while (true)`; hold all candidates/results in memory; rely on
manual process termination.

## Consequences

Coordinator state and metrics are more explicit, but runs become controllable,
testable, and reproducible.

## Evidence

M5.1 implements deterministic lazy generation, bounded batch persistence,
SearchRun lifecycle/progress, all three stop-condition policies, explicit source
exhaustion, and concurrent cancellation. M5.5 makes cancellation immediately
terminal and transactionally cancels non-started jobs/experiments plus their
unpublished outbox intents. Progress is emitted after lifecycle and batch
changes on a search-run-specific STOMP topic. Worker meters expose active
replicas, processed outcomes, failures, and queue depth. PostgreSQL/RabbitMQ
tests cover cancellation races and one-to-three replica scaling.
M7 registers Random and Genetic together for per-run selection and adds active
search, generated candidate, job outcome/duration/duplicate, and pending-outbox
meters. Structured async logs carry applicable correlation, search-run, job,
experiment, and event identifiers; the dashboard combines durable counters with
WebSocket updates and polling fallback.
