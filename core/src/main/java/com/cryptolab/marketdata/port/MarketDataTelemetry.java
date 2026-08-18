package com.cryptolab.marketdata.port;

import java.time.Duration;

public interface MarketDataTelemetry {

    void reconnectAttempted();

    void gapCandlesRecovered(int count);

    void candlePublished(Duration latency);
}
