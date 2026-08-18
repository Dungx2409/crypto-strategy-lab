# ADR-008: Separate News Collection and Sentiment Analysis

## Context

News providers and sentiment models have different availability, contracts, and
replacement cycles.

## Decision

Normalize provider output to `NewsItem`; pass that to a versioned
`SentimentAnalyzer`; strategies consume normalized `SentimentResult` only.

## Alternatives

Call a model inside the news client or directly inside a strategy.

## Consequences

Either side can be replaced and failed independently. Model/input/preprocessing
metadata must be persisted honestly.

## Evidence

M6 implements the separate provider/analyzer/store/telemetry ports through
`NewsCollector`, a CryptoCompare provider adapter, the versioned local keyword
analyzer, and JDBC persistence. Unit tests prove that either adapter can fail
without propagating into the other flow; API and PostgreSQL integration tests
prove Market Data remains operational and stored news remains readable while
News is DOWN.
M7 presents News and Sentiment health independently in System Status. The final
isolation test also guards Search, Backtest, and Leaderboard dependency paths,
while the dashboard remains usable when collection is degraded.
