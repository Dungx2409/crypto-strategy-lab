package com.cryptolab.marketdata.port;

import com.cryptolab.marketdata.domain.Candle;

@FunctionalInterface
public interface CandleListener {
    void onCandle(Candle candle);

    default void onConnected() {}

    default void onDisconnected(Throwable cause) {}
}
