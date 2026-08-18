# ADR-001: Market Data Provider and Adapter

## Context

Exchange payloads, URLs, reconnect behavior, and rate limits change independently
from strategy and experiment policy.

## Decision

Core owns `MarketDataProvider`/`MarketDataPort` and normalized `Candle` contracts.
Exchange-specific code stays in infrastructure adapters.

## Alternatives

Call Binance directly from strategies; expose Binance DTOs through the API.

## Consequences

Provider replacement and deterministic tests are easier, at the cost of explicit
mapping code.

## Evidence

Core dependency rules and ArchUnit forbid domain-to-adapter dependencies. M2's
package-private Binance DTOs map to normalized candles inside the adapter. The
M7 proof suite exercises historical/realtime behavior, generation-scoped
reconnect, boundary-inclusive gap recovery, and duplicate-safe persistence.
