package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Trade(
        Instant entryTime,
        BigDecimal entryPrice,
        Instant exitTime,
        BigDecimal exitPrice,
        BigDecimal quantity,
        BigDecimal fee,
        BigDecimal pnl) {

    public Trade {
        Objects.requireNonNull(entryTime, "entryTime must not be null");
        Objects.requireNonNull(entryPrice, "entryPrice must not be null");
        Objects.requireNonNull(exitTime, "exitTime must not be null");
        Objects.requireNonNull(exitPrice, "exitPrice must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(fee, "fee must not be null");
        Objects.requireNonNull(pnl, "pnl must not be null");
        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("exitTime must not be before entryTime");
        }
        if (entryPrice.signum() <= 0 || exitPrice.signum() <= 0 || quantity.signum() <= 0) {
            throw new IllegalArgumentException("prices and quantity must be positive");
        }
        if (fee.signum() < 0) {
            throw new IllegalArgumentException("fee must not be negative");
        }
    }
}
