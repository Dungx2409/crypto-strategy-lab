# ADR-003: Strategy Plugin Registry

## Context

Adding MACD must not modify Backtester, Evaluator, Ranking, controllers, or a
strategy-specific database schema.

## Decision

Core defines `Strategy`, `StrategyFactory`, and `StrategyRegistry`. Spring wiring
will discover factories in an outer layer; parameters are versioned flexible
snapshots.

## Alternatives

Scattered `switch(strategyType)` statements; inheritance tied to persistence.

## Consequences

New strategies require one implementation/factory. Parameter validation and
type/version uniqueness must be centralized.

## Evidence

M3 implements `SpringStrategyRegistry`, four independently discovered baseline
factory beans, and `GET /api/v1/strategies` through the registry abstraction.
M7 adds production `MacdStrategy` and `MacdStrategyFactory` only; it does not
change Backtester, Evaluator, Ranking, controllers, or Flyway migrations. MACD
uses deterministic EMA seeding from the first available close and consumes only
the ordered candle prefix passed by the backtester. `StrategyExtensionArchitectureTest`
proves registry creation, and ArchUnit prevents Experiment/API/Worker code from
depending on the MACD implementation. The dashboard exposes only schemas
discovered through the registry.
