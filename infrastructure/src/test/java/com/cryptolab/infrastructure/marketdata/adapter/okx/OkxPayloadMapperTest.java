package com.cryptolab.infrastructure.marketdata.adapter.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OkxPayloadMapperTest {

    private final OkxPayloadMapper mapper = new OkxPayloadMapper(new ObjectMapper());

    @Test
    void mapsHistoricalPayloadToExchangeNeutralCandles() {
        String payload = """
                {"code":"0","msg":"","data":[
                  ["1723942800000","100.10","110.20","90.30","105.40","12.50","0","0","1"]
                ]}
                """;

        var candle = mapper.historical(payload, new TradingPair("BTCUSDT"), Timeframe.M5)
                .getFirst();

        assertThat(candle.symbol()).isEqualTo("BTCUSDT");
        assertThat(candle.timeframe()).isEqualTo(Timeframe.M5);
        assertThat(candle.openTime()).isEqualTo(Instant.ofEpochMilli(1723942800000L));
        assertThat(candle.close()).hasToString("105.40");
    }

    @Test
    void mapsRealtimeOpenAndClosedCandlesAndIgnoresSubscriptionAcknowledgement() {
        TradingPair pair = new TradingPair("BTCUSDT");
        String acknowledgement = """
                {"event":"subscribe","arg":{"channel":"candle5m","instId":"BTC-USDT"}}
                """;
        String open = realtime("0");
        String closed = realtime("1");

        assertThat(mapper.realtime(acknowledgement, pair, Timeframe.M5)).isEmpty();
        assertThat(mapper.realtime(open, pair, Timeframe.M5)).get().extracting(update -> update.closed()).isEqualTo(false);
        assertThat(mapper.realtime(closed, pair, Timeframe.M5)).get().extracting(update -> update.closed()).isEqualTo(true);
    }

    private String realtime(String confirmed) {
        return """
                {"arg":{"channel":"candle5m","instId":"BTC-USDT"},"data":[
                  ["1723942800000","100.10","110.20","90.30","105.40","12.50","0","0","%s"]
                ]}
                """.formatted(confirmed);
    }
}
