package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record ExecutionConfig(
        BigDecimal initialCapital,
        BigDecimal feeRate,
        boolean allowShort,
        String fillPolicy,
        String engineVersion,
        BigDecimal positionSizePct,
        BigDecimal stopLossPct,
        BigDecimal takeProfitPct,
        BigDecimal trailingStopPct) {

    public ExecutionConfig(
            BigDecimal initialCapital,
            BigDecimal feeRate,
            boolean allowShort,
            String fillPolicy,
            String engineVersion) {
        this(initialCapital, feeRate, allowShort, fillPolicy, engineVersion, new BigDecimal("100"), null, null, null);
    }

    public ExecutionConfig(
            BigDecimal initialCapital,
            BigDecimal feeRate,
            boolean allowShort,
            String fillPolicy,
            String engineVersion,
            BigDecimal positionSizePct) {
        this(initialCapital, feeRate, allowShort, fillPolicy, engineVersion, positionSizePct, null, null, null);
    }

    public ExecutionConfig(
            BigDecimal initialCapital,
            BigDecimal feeRate,
            boolean allowShort,
            String fillPolicy,
            String engineVersion,
            BigDecimal positionSizePct,
            BigDecimal stopLossPct,
            BigDecimal takeProfitPct) {
        this(initialCapital, feeRate, allowShort, fillPolicy, engineVersion,
                positionSizePct, stopLossPct, takeProfitPct, null);
    }

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
        positionSizePct = positionSizePct == null ? new BigDecimal("100") : positionSizePct;
        if (positionSizePct.signum() <= 0 || positionSizePct.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("positionSizePct must be in (0, 100]");
        }
        if (stopLossPct != null && (stopLossPct.signum() <= 0 || stopLossPct.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("stopLossPct must be in (0, 100]");
        }
        if (takeProfitPct != null && (takeProfitPct.signum() <= 0 || takeProfitPct.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("takeProfitPct must be in (0, 100]");
        }
        if (trailingStopPct != null && (trailingStopPct.signum() <= 0 || trailingStopPct.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("trailingStopPct must be in (0, 100]");
        }
    }
}
