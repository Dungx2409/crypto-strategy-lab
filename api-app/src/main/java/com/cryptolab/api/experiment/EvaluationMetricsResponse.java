package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.EvaluationMetrics;
import java.math.BigDecimal;

public record EvaluationMetricsResponse(
        BigDecimal totalReturnPct,
        BigDecimal maxDrawdownPct,
        int totalTrades,
        BigDecimal winRatePct,
        BigDecimal score) {

    static EvaluationMetricsResponse from(EvaluationMetrics metrics) {
        return metrics == null
                ? null
                : new EvaluationMetricsResponse(
                        metrics.totalReturnPct(),
                        metrics.maxDrawdownPct(),
                        metrics.totalTrades(),
                        metrics.winRatePct(),
                        metrics.score());
    }
}
