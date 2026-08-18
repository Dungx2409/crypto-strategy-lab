package com.cryptolab.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Signal(SignalType type, BigDecimal strength, Instant at, String reason) {

    private static final BigDecimal MIN_STRENGTH = BigDecimal.ONE.negate();
    private static final BigDecimal MAX_STRENGTH = BigDecimal.ONE;

    public Signal {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(strength, "strength must not be null");
        Objects.requireNonNull(at, "at must not be null");
        if (strength.compareTo(MIN_STRENGTH) < 0 || strength.compareTo(MAX_STRENGTH) > 0) {
            throw new IllegalArgumentException("strength must be in [-1, 1]");
        }
        reason = reason == null ? "" : reason;
    }
}
