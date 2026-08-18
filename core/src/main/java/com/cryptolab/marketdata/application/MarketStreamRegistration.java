package com.cryptolab.marketdata.application;

@FunctionalInterface
public interface MarketStreamRegistration extends AutoCloseable {

    @Override
    void close();
}
