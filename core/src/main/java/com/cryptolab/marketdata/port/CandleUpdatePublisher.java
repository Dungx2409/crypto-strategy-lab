package com.cryptolab.marketdata.port;

import com.cryptolab.marketdata.domain.Candle;

@FunctionalInterface
public interface CandleUpdatePublisher {

    void publishClosed(Candle candle);
}
