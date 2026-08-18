package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.port.CandleUpdatePublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
final class StompCandleUpdatePublisher implements CandleUpdatePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    StompCandleUpdatePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishClosed(Candle candle) {
        String destination = "/topic/market/"
                + candle.symbol()
                + "/"
                + candle.timeframe().exchangeCode();
        messagingTemplate.convertAndSend(destination, MarketCandleEvent.closed(candle));
    }
}
