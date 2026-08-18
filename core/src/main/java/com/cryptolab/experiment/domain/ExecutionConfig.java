package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record ExecutionConfig(
        BigDecimal initialCapital,
        BigDecimal feeRate,
        boolean allowShort,
        String fillPolicy,
        String engineVersion) {

    public ExecutionConfig {
        Objects.requireNonNull(initialCapital, "initialCapital must not be null");
        Objects.requireNonNull(feeRate, "feeRate must not be null");
        if (initialCapital.signum() <= 0) {
            throw new IllegalArgumentException("initialCapital must be positive");
        }
        if (feeRate.signum() < 0) {
            throw new IllegalArgumentException("feeRate must not be negative");
        }
        if (fillPolicy == null || fillPolicy.isBlank()) {
            throw new IllegalArgumentException("fillPolicy must not be blank");
        }
        if (engineVersion == null || engineVersion.isBlank()) {
            throw new IllegalArgumentException("engineVersion must not be blank");
        }
    }
}
