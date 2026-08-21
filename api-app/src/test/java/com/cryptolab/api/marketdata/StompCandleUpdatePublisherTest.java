package com.cryptolab.api.marketdata;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.CandleUpdate;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class StompCandleUpdatePublisherTest {

    @Test
    void publishesExchangeNeutralInProgressCandleEvent() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        Candle candle = new Candle(
                "BTCUSDT",
                Timeframe.M1,
                Instant.parse("2026-08-18T01:50:00Z"),
                new BigDecimal("100.10"),
                new BigDecimal("110.20"),
                new BigDecimal("90.30"),
                new BigDecimal("105.40"),
                new BigDecimal("12.50"));

        new StompCandleUpdatePublisher(template).publish(CandleUpdate.inProgress(candle));

        verify(template).convertAndSend(
                "/topic/market/BTCUSDT/1m",
                new MarketCandleEvent(
                        "CANDLE_UPDATE",
                        "BTCUSDT",
                        "1m",
                        candle.openTime(),
                        "100.10",
                        "110.20",
                        "90.30",
                        "105.40",
                        "12.50",
                        false));
    }
}
