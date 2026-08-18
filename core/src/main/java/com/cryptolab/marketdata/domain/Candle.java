package com.cryptolab.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record Candle(
        String symbol,
        Timeframe timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume) {

    public Candle {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Candle symbol must not be blank");
        }
        symbol = symbol.trim().toUpperCase(Locale.ROOT);
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(openTime, "openTime must not be null");
        Objects.requireNonNull(open, "open must not be null");
        Objects.requireNonNull(high, "high must not be null");
        Objects.requireNonNull(low, "low must not be null");
        Objects.requireNonNull(close, "close must not be null");
        Objects.requireNonNull(volume, "volume must not be null");

        if (high.compareTo(open.max(close).max(low)) < 0) {
            throw new IllegalArgumentException("high must be greater than or equal to open, low, and close");
        }
        if (low.compareTo(open.min(close).min(high)) > 0) {
            throw new IllegalArgumentException("low must be less than or equal to open, high, and close");
        }
        if (volume.signum() < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }
}
