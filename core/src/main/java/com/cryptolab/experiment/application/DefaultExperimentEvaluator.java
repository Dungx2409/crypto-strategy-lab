package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.EquityPoint;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.Trade;
import com.cryptolab.experiment.port.ExperimentEvaluator;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

public final class DefaultExperimentEvaluator implements ExperimentEvaluator {

    public static final String VERSION = "return-minus-half-drawdown-v1";
    private static final MathContext MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Evaluation evaluate(
            BacktestResult result,
            ExecutionConfig executionConfig,
            Instant evaluatedAt) {
        BigDecimal totalReturn = result.endingCapital()
                .subtract(executionConfig.initialCapital())
                .divide(executionConfig.initialCapital(), MATH_CONTEXT)
                .multiply(ONE_HUNDRED);
        BigDecimal maxDrawdown = calculateMaxDrawdown(result);
        BigDecimal winRate = calculateWinRate(result);
        BigDecimal score = totalReturn.subtract(maxDrawdown.abs().multiply(HALF));
        EvaluationMetrics metrics = new EvaluationMetrics(
                normalize(totalReturn),
                normalize(maxDrawdown),
                result.trades().size(),
                normalize(winRate),
                normalize(score));
        return new Evaluation(result.experimentId(), metrics, VERSION, evaluatedAt);
    }

    private static BigDecimal calculateWinRate(BacktestResult result) {
        if (result.trades().isEmpty()) {
            return BigDecimal.ZERO;
        }
        long winningTrades = result.trades().stream()
                .map(Trade::pnl)
                .filter(pnl -> pnl.signum() > 0)
                .count();
        return BigDecimal.valueOf(winningTrades)
                .divide(BigDecimal.valueOf(result.trades().size()), MATH_CONTEXT)
                .multiply(ONE_HUNDRED);
    }

    private static BigDecimal calculateMaxDrawdown(BacktestResult result) {
        BigDecimal peak = null;
        BigDecimal maximumDrawdown = BigDecimal.ZERO;
        for (EquityPoint point : result.equityCurve()) {
            if (peak == null || point.equity().compareTo(peak) > 0) {
                peak = point.equity();
            }
            if (peak.signum() == 0) {
                continue;
            }
            BigDecimal drawdown = point.equity()
                    .subtract(peak)
                    .divide(peak, MATH_CONTEXT)
                    .multiply(ONE_HUNDRED);
            if (drawdown.compareTo(maximumDrawdown) < 0) {
                maximumDrawdown = drawdown;
            }
        }
        return maximumDrawdown;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
