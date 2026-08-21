package com.cryptolab.marketdata.port;

import com.cryptolab.marketdata.domain.CandleUpdate;

@FunctionalInterface
public interface CandleUpdatePublisher {

    void publish(CandleUpdate update);
}
