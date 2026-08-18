package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record EvaluationMetrics(
        BigDecimal totalReturnPct,
        BigDecimal maxDrawdownPct,
        int totalTrades,
        BigDecimal score) {

    public EvaluationMetrics {
        Objects.requireNonNull(totalReturnPct, "totalReturnPct must not be null");
        Objects.requireNonNull(maxDrawdownPct, "maxDrawdownPct must not be null");
        Objects.requireNonNull(score, "score must not be null");
        if (totalTrades < 0) {
            throw new IllegalArgumentException("totalTrades must not be negative");
        }
    }
}
