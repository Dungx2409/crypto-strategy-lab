# ADR-007: Immutable Experiment Provenance

## Context

A leaderboard result must be explainable and reproducible months later even if
defaults, code, or external data change.

## Decision

Snapshot candidate, strategy versions/parameters, combination policy, immutable
dataset identity/checksum, execution config, generator/seed, evaluator version,
and build/Git version when an experiment starts.

## Alternatives

Resolve current configuration by foreign key at read time; store only final
metrics.

## Consequences

Storage is duplicated intentionally. Completed configuration becomes immutable
and reruns reference the source experiment.

## Evidence

M4 captures candidate hash and definitions, policy, complete immutable dataset,
dataset checksum/version/time range, execution and engine settings,
generator/config/seed, evaluator formula version, code/build versions,
timestamps, artifacts, and metrics. PostgreSQL integration tests reconstruct
the source inputs and verify a rerun records its source and matching metrics.
M7 materializes the dashboard candle snapshot through the same dataset contract
before starting a search. `ExperimentPipelineIT` follows leaderboard rank #1's
`experimentId` and asserts every reproduction field plus signals, trades, and
metrics remains reachable.
