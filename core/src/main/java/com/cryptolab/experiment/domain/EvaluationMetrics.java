package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record EvaluationMetrics(
        BigDecimal totalReturnPct,
        BigDecimal maxDrawdownPct,
        int totalTrades,
        BigDecimal winRatePct,
        BigDecimal score) {

    public EvaluationMetrics {
        Objects.requireNonNull(totalReturnPct, "totalReturnPct must not be null");
        Objects.requireNonNull(maxDrawdownPct, "maxDrawdownPct must not be null");
        winRatePct = winRatePct == null ? BigDecimal.ZERO : winRatePct;
        Objects.requireNonNull(score, "score must not be null");
        if (totalTrades < 0) {
            throw new IllegalArgumentException("totalTrades must not be negative");
        }
        if (winRatePct.signum() < 0 || winRatePct.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("winRatePct must be between 0 and 100");
        }
    }

    public EvaluationMetrics(
            BigDecimal totalReturnPct,
            BigDecimal maxDrawdownPct,
            int totalTrades,
            BigDecimal score) {
        this(totalReturnPct, maxDrawdownPct, totalTrades, BigDecimal.ZERO, score);
    }
}
