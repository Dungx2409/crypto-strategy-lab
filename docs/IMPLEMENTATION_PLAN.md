# Approved Implementation Plan

`FEATURE_SPEC.md` is the primary product and architecture contract. This plan
defines implementation order; it does not override normative requirements in
the specification.

## Delivery rules

1. Implement one milestone at a time.
2. Inspect existing code before each change and preserve working behavior.
3. Do not advance while the current milestone's tests fail.
4. Keep `core` independent from Spring MVC, JPA, RabbitMQ, and provider DTOs.
5. Use Flyway for every database change.
6. Record automated evidence for architectural invariants where practical.

## Milestones

### P0 — Repository/Foundation

- Establish the Maven reactor and Maven Wrapper.
- Pin Java and build-tool requirements.
- Establish module dependency direction.
- Add repository hygiene, CI, environment template, and requirement tracking.
- Do not implement business or infrastructure behavior.

### M1 — Architecture Skeleton

- Add bounded-context packages, domain models, and ports.
- Add Spring Boot bootstrap applications.
- Add PostgreSQL, Flyway, Docker Compose, and ArchUnit boundaries.
- Create required architecture documents and ADRs.

### M2 — Market Data Walking Skeleton

- Deliver Binance historical and realtime data through normalized candles.
- Persist/deduplicate candles and recover gaps after reconnect.
- Expose market REST/WebSocket contracts and the initial chart.

### M3 — Strategy Plugin

- Implement registry/factory, MA, RSI, BB, and SR strategies.
- Implement majority and weighted combination policies.
- Prove extension isolation with automated tests.

### M4 — Experiment Pipeline

- Deliver candidate, deterministic backtest, evaluation, ranking, leaderboard,
  experiment details, and immutable provenance.

### M5 — Search + RabbitMQ + Workers

- Add deterministic Random and replaceable Genetic generators.
- Add bounded search orchestration, durable jobs, independently scalable
  workers, retries, DLQ, idempotency, outbox/inbox, cancellation, and progress.

### M6 — News + Sentiment

- Add replaceable news and sentiment ports/adapters.
- Persist honest model metadata and prove failure isolation.

### M7 — Dashboard + Architecture Proof

- Complete all dashboard panels, observability, failure demonstrations,
  scale evidence, reproducibility evidence, and final documentation.

## Required dependency direction

```text
api-app / worker-app -> infrastructure -> core
```

`integration-tests` may depend on runnable applications in test scope. Reverse
dependencies are forbidden.
