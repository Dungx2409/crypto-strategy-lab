package com.cryptolab.infrastructure.marketdata.adapter.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BinancePayloadMapperTest {

    private final BinancePayloadMapper mapper = new BinancePayloadMapper(new ObjectMapper());

    @Test
    void mapsHistoricalArrayToExchangeNeutralCandle() {
        String payload = """
                [[1723942800000,"100.10","110.20","90.30","105.40","12.50",1723943099999]]
                """;

        var candle = mapper.historical(payload, new TradingPair("BTCUSDT"), Timeframe.M5).getFirst();

        assertThat(candle.symbol()).isEqualTo("BTCUSDT");
        assertThat(candle.timeframe()).isEqualTo(Timeframe.M5);
        assertThat(candle.openTime()).isEqualTo(Instant.ofEpochMilli(1723942800000L));
        assertThat(candle.close()).hasToString("105.40");
    }

    @Test
    void mapsOpenAndClosedRealtimeKlinesToExchangeNeutralUpdates() {
        String open = realtime(false);
        String closed = realtime(true);

        assertThat(mapper.realtime(open).closed()).isFalse();
        assertThat(mapper.realtime(closed).closed()).isTrue();
        assertThat(mapper.realtime(open).candle().symbol()).isEqualTo("BTCUSDT");
    }

    private String realtime(boolean closed) {
        return """
                {
                  "e":"kline",
                  "s":"BTCUSDT",
                  "k":{
                    "t":1723942800000,
                    "i":"5m",
                    "o":"100.10",
                    "h":"110.20",
                    "l":"90.30",
                    "c":"105.40",
                    "v":"12.50",
                    "x":%s
                  }
                }
                """.formatted(closed);
    }
}
