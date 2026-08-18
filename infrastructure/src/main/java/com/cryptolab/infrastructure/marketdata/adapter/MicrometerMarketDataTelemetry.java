package com.cryptolab.infrastructure.marketdata.adapter;

import com.cryptolab.marketdata.port.MarketDataTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public final class MicrometerMarketDataTelemetry implements MarketDataTelemetry {

    private final Counter reconnects;
    private final Counter recoveredCandles;
    private final Timer uiLatency;

    public MicrometerMarketDataTelemetry(MeterRegistry registry) {
        reconnects = Counter.builder("crypto.market.ws.reconnect")
                .description("Binance WebSocket reconnect attempts")
                .register(registry);
        recoveredCandles = Counter.builder("crypto.market.gap.candles.recovered")
                .description("Candles inserted during gap recovery")
                .register(registry);
        uiLatency = Timer.builder("crypto.market.candle.to.ui.latency")
                .description("Closed candle to UI publication latency")
                .register(registry);
    }

    @Override
    public void reconnectAttempted() {
        reconnects.increment();
    }

    @Override
    public void gapCandlesRecovered(int count) {
        recoveredCandles.increment(count);
    }

    @Override
    public void candlePublished(Duration latency) {
        uiLatency.record(latency);
    }
}
