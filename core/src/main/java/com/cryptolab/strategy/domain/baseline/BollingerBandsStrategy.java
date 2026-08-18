package com.cryptolab.strategy.domain.baseline;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class BollingerBandsStrategy implements Strategy {

    public static final String TYPE = "BB";
    public static final String VERSION = "1.0";

    private final int window;
    private final BigDecimal deviationMultiplier;

    public BollingerBandsStrategy(int window, BigDecimal deviationMultiplier) {
        if (window < 2) {
            throw new IllegalArgumentException("window must be at least 2");
        }
        if (deviationMultiplier == null || deviationMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("deviationMultiplier must be positive");
        }
        this.window = window;
        this.deviationMultiplier = deviationMultiplier;
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(
                TYPE,
                VERSION,
                Map.of("window", window, "deviationMultiplier", deviationMultiplier));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        List<Candle> candles = context.candles();
        if (candles.size() < window) {
            return BaselineStrategySupport.hold(context, "insufficient candles for Bollinger Bands");
        }

        int from = candles.size() - window;
        BigDecimal mean = BaselineStrategySupport.averageClose(candles, from, candles.size());
        BigDecimal squaredDifferenceSum = BigDecimal.ZERO;
        for (int index = from; index < candles.size(); index++) {
            BigDecimal difference = candles.get(index).close().subtract(mean);
            squaredDifferenceSum = squaredDifferenceSum.add(difference.multiply(difference));
        }
        BigDecimal variance = squaredDifferenceSum.divide(
                BigDecimal.valueOf(window), BaselineStrategySupport.MATH_CONTEXT);
        BigDecimal standardDeviation = variance.sqrt(BaselineStrategySupport.MATH_CONTEXT);
        BigDecimal bandDistance = standardDeviation.multiply(
                deviationMultiplier, BaselineStrategySupport.MATH_CONTEXT);
        BigDecimal lowerBand = mean.subtract(bandDistance);
        BigDecimal upperBand = mean.add(bandDistance);
        BigDecimal currentClose = candles.get(candles.size() - 1).close();

        if (currentClose.compareTo(lowerBand) < 0) {
            return BaselineStrategySupport.buy(context, "close is below the lower Bollinger Band");
        }
        if (currentClose.compareTo(upperBand) > 0) {
            return BaselineStrategySupport.sell(context, "close is above the upper Bollinger Band");
        }
        return BaselineStrategySupport.hold(context, "close is within the Bollinger Bands");
    }
}
