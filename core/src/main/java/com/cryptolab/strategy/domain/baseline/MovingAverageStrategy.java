package com.cryptolab.strategy.domain.baseline;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class MovingAverageStrategy implements Strategy {

    public static final String TYPE = "MA";
    public static final String VERSION = "1.0";

    private final int fastPeriod;
    private final int slowPeriod;

    public MovingAverageStrategy(int fastPeriod, int slowPeriod) {
        if (fastPeriod < 1) {
            throw new IllegalArgumentException("fastPeriod must be positive");
        }
        if (slowPeriod <= fastPeriod) {
            throw new IllegalArgumentException("slowPeriod must be greater than fastPeriod");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(
                TYPE,
                VERSION,
                Map.of("fastPeriod", fastPeriod, "slowPeriod", slowPeriod));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        List<Candle> candles = context.candles();
        if (candles.size() < slowPeriod + 1) {
            return BaselineStrategySupport.hold(context, "insufficient candles for moving-average crossover");
        }

        int size = candles.size();
        BigDecimal previousFast = BaselineStrategySupport.averageClose(
                candles, size - fastPeriod - 1, size - 1);
        BigDecimal previousSlow = BaselineStrategySupport.averageClose(
                candles, size - slowPeriod - 1, size - 1);
        BigDecimal currentFast = BaselineStrategySupport.averageClose(
                candles, size - fastPeriod, size);
        BigDecimal currentSlow = BaselineStrategySupport.averageClose(
                candles, size - slowPeriod, size);

        if (previousFast.compareTo(previousSlow) <= 0 && currentFast.compareTo(currentSlow) > 0) {
            return BaselineStrategySupport.buy(context, "fast moving average crossed above slow moving average");
        }
        if (previousFast.compareTo(previousSlow) >= 0 && currentFast.compareTo(currentSlow) < 0) {
            return BaselineStrategySupport.sell(context, "fast moving average crossed below slow moving average");
        }
        return BaselineStrategySupport.hold(context, "no moving-average crossover");
    }
}
