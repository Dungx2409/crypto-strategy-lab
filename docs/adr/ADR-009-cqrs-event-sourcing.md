# ADR-009: No Full CQRS or Event Sourcing

## Context

The project needs an efficient leaderboard, durable events, audit provenance,
and failure recovery, but does not require all state to be reconstructed from an
event log.

## Decision

Persist current state in PostgreSQL, use transactional outbox events, and allow
an idempotent leaderboard projection. Do not adopt full CQRS or Event Sourcing.

## Alternatives

Separate read/write services and databases; make events the source of truth.

## Consequences

Operational complexity stays proportionate. Projection rebuild and event replay
policies must still be explicit.

## Evidence

PostgreSQL stores current state, outbox, processed-event inbox, and leaderboard
projection tables. M5 implements confirmed outbox publication and idempotent
asynchronous evaluation/ranking; duplicate events do not duplicate leaderboard
state. M7 exposes outbox backlog and queue state operationally without changing
the persistence model into Event Sourcing.
