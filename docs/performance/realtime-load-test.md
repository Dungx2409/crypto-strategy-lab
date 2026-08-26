# Realtime load proof

The load profile is one application node, 1,000 WebSocket sessions, four market
subscriptions per session, and a ten-minute hold. Market subscriptions are
reference-counted by `(symbol, timeframe)`, so 4,000 browser subscriptions use
four upstream streams for this BTC profile rather than 4,000 exchange sockets.

Run the API with PostgreSQL and RabbitMQ, confirm the market provider is healthy,
then run:

```bash
mvn -Pload-test -pl load-tests gatling:test \
  -Dgatling.simulationClass=com.cryptolab.load.RealtimeLoadSimulation
```

Useful overrides are `-Dload.baseUrl`, `-Dload.users`, `-Dload.rampSeconds`, and
`-Dload.holdSeconds`. The committed acceptance gates are:

- at least 99% successful WebSocket connections;
- at least 99% of sessions receive a candle on every one of four streams;
- first-candle p95 below one second;
- no more than 1% failed connection/message checks;
- zero server-side errors during the measurement window, verified from application
  logs and the `http.server.requests` 5xx counters before and after the run.

Keep the generated Gatling HTML report with the release evidence. A reduced
smoke run can use `-Dload.users=10 -Dload.rampSeconds=10 -Dload.holdSeconds=30`.
The test module compiling is not the performance proof: only a full run against
the documented topology produces the required latency and error measurements.
