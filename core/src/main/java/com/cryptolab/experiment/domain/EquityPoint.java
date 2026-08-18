package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record EquityPoint(Instant at, BigDecimal equity) {

    public EquityPoint {
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(equity, "equity must not be null");
        if (equity.signum() < 0) {
            throw new IllegalArgumentException("equity must not be negative");
        }
    }
}
