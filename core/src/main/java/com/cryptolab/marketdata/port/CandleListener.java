package com.cryptolab.marketdata.port;

import com.cryptolab.marketdata.domain.CandleUpdate;

@FunctionalInterface
public interface CandleListener {
    void onCandle(CandleUpdate update);

    default void onConnected() {}

    default void onDisconnected(Throwable cause) {}
}
