package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.domain.CandleUpdate;
import com.cryptolab.marketdata.port.CandleUpdatePublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
final class StompCandleUpdatePublisher implements CandleUpdatePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final Counter publishedMessages;

    StompCandleUpdatePublisher(SimpMessagingTemplate messagingTemplate) {
        this(messagingTemplate, new SimpleMeterRegistry());
    }

    @Autowired
    StompCandleUpdatePublisher(
            SimpMessagingTemplate messagingTemplate, MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.publishedMessages = Counter.builder("crypto.market.stomp.messages.published")
                .description("Market candle messages published to STOMP topics")
                .register(meterRegistry);
    }

    @Override
    public void publish(CandleUpdate update) {
        var candle = update.candle();
        String destination = "/topic/market/"
                + candle.symbol()
                + "/"
                + candle.timeframe().exchangeCode();
        messagingTemplate.convertAndSend(destination, MarketCandleEvent.from(update));
        publishedMessages.increment();
    }
}
