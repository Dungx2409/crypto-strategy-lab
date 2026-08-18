package com.cryptolab.marketdata.domain;

import java.util.Locale;

public record TradingPair(String symbol) {

    public TradingPair {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Trading pair symbol must not be blank");
        }
        symbol = symbol.trim().toUpperCase(Locale.ROOT);
    }
}
