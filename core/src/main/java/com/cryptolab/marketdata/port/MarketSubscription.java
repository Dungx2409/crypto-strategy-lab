package com.cryptolab.marketdata.port;

public interface MarketSubscription extends AutoCloseable {

    boolean isActive();

    @Override
    void close();
}
