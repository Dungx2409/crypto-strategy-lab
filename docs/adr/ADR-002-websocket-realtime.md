# ADR-002: WebSocket for Realtime UI

## Context

The dashboard needs low-latency candle, progress, and leaderboard updates without
polling unrelated state.

## Decision

Use Spring WebSocket topics for server-to-browser realtime updates and REST for
commands/snapshots.

## Alternatives

Frequent REST polling; Server-Sent Events; provider WebSocket directly in the
browser.

## Consequences

Subscriptions can be isolated by symbol/timeframe/search run. Connection
lifecycle and reconnect behavior require explicit handling.

## Evidence

M2 configures the STOMP endpoint `/ws`, publishes closed candles to
`/topic/market/{symbol}/{timeframe}`, and reference-counts subscriptions so a
timeframe change releases only the prior market stream. The static market
client explicitly sends `UNSUBSCRIBE`, reloads only `/api/v1/market/candles`,
and then subscribes to the replacement topic.

M5.5 publishes search snapshots to `/topic/search/{searchRunId}` and leaderboard
events to `/topic/leaderboard/{searchRunId}`. The RabbitMQ-to-STOMP listeners
record `processed_events`, so an already-recorded redelivery is acknowledged
without another browser update. WebSocket publication is kept behind core
output ports and cannot roll back durable search state.
