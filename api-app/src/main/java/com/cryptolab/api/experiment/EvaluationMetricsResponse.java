package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.EvaluationMetrics;
import java.math.BigDecimal;

public record EvaluationMetricsResponse(
        BigDecimal totalReturnPct,
        BigDecimal maxDrawdownPct,
        int totalTrades,
        BigDecimal winRatePct,
        BigDecimal score,
        BigDecimal netProfit,
        BigDecimal endingCapital) {

    static EvaluationMetricsResponse from(EvaluationMetrics metrics, BigDecimal initialCapital) {
        BigDecimal netProfit = metrics == null
                ? null
                : initialCapital.multiply(metrics.totalReturnPct()).movePointLeft(2);
        return metrics == null
                ? null
                : new EvaluationMetricsResponse(
                        metrics.totalReturnPct(),
                        metrics.maxDrawdownPct(),
                        metrics.totalTrades(),
                        metrics.winRatePct(),
                        metrics.score(),
                        netProfit,
                        initialCapital.add(netProfit));
    }
}
