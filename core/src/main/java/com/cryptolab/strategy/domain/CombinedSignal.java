package com.cryptolab.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record CombinedSignal(SignalType type, BigDecimal score, Instant at) {

    public CombinedSignal {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(at, "at must not be null");
    }
}
