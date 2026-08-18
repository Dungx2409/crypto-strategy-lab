package com.cryptolab.strategy.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record WeightedSignal(StrategyDescriptor strategy, Signal signal, BigDecimal weight) {

    public WeightedSignal {
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(signal, "signal must not be null");
        Objects.requireNonNull(weight, "weight must not be null");
        if (weight.signum() < 0) {
            throw new IllegalArgumentException("weight must not be negative");
        }
    }
}
