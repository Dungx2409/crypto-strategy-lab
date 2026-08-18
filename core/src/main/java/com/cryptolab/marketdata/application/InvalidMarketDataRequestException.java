package com.cryptolab.marketdata.application;

public final class InvalidMarketDataRequestException extends RuntimeException {

    private final String code;

    public InvalidMarketDataRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
