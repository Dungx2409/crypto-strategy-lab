# ADR-004: Separate Backtester and Evaluator

## Context

Trade simulation rules and result scoring evolve for different reasons and must
be independently testable/versioned.

## Decision

Backtesting produces trades/signals/results through `BacktestPort`; evaluation
calculates versioned metrics and ranking consumes evaluations.

## Alternatives

One service that simulates, scores, ranks, and persists everything.

## Consequences

The pipeline has explicit contracts and avoids a God Service. Scoring and
tie-break changes remain isolated from candle execution and strategy plugins.

## Evidence

M4 provides `DeterministicBacktestEngine`, `DefaultExperimentEvaluator`, and
`DefaultRankingService` as separate components. Unit tests exercise each stage,
and ArchUnit forbids Evaluator/Ranking from depending on the concrete engine.
