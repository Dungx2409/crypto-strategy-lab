package com.cryptolab.marketdata.application;

public final class MarketDataUnavailableException extends RuntimeException {

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
