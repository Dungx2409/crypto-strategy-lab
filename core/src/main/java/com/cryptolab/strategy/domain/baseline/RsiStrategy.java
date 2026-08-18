package com.cryptolab.strategy.domain.baseline;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class RsiStrategy implements Strategy {

    public static final String TYPE = "RSI";
    public static final String VERSION = "1.0";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final int period;
    private final BigDecimal oversold;
    private final BigDecimal overbought;

    public RsiStrategy(int period, BigDecimal oversold, BigDecimal overbought) {
        if (period < 2) {
            throw new IllegalArgumentException("period must be at least 2");
        }
        if (oversold == null || overbought == null) {
            throw new IllegalArgumentException("RSI thresholds must not be null");
        }
        if (oversold.signum() < 0
                || overbought.compareTo(ONE_HUNDRED) > 0
                || oversold.compareTo(overbought) >= 0) {
            throw new IllegalArgumentException("RSI thresholds must satisfy 0 <= oversold < overbought <= 100");
        }
        this.period = period;
        this.oversold = oversold;
        this.overbought = overbought;
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(
                TYPE,
                VERSION,
                Map.of("period", period, "oversold", oversold, "overbought", overbought));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        List<Candle> candles = context.candles();
        if (candles.size() < period + 1) {
            return BaselineStrategySupport.hold(context, "insufficient candles for RSI");
        }

        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        int firstChangeIndex = candles.size() - period;
        for (int index = firstChangeIndex; index < candles.size(); index++) {
            BigDecimal change = candles.get(index).close().subtract(candles.get(index - 1).close());
            if (change.signum() > 0) {
                gains = gains.add(change);
            } else if (change.signum() < 0) {
                losses = losses.add(change.abs());
            }
        }

        BigDecimal rsi = calculateRsi(gains, losses);
        if (rsi.compareTo(oversold) <= 0) {
            return BaselineStrategySupport.buy(context, "RSI is at or below the oversold threshold");
        }
        if (rsi.compareTo(overbought) >= 0) {
            return BaselineStrategySupport.sell(context, "RSI is at or above the overbought threshold");
        }
        return BaselineStrategySupport.hold(context, "RSI is between configured thresholds");
    }

    private static BigDecimal calculateRsi(BigDecimal gains, BigDecimal losses) {
        if (gains.signum() == 0 && losses.signum() == 0) {
            return new BigDecimal("50");
        }
        if (losses.signum() == 0) {
            return ONE_HUNDRED;
        }
        if (gains.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal relativeStrength = gains.divide(losses, BaselineStrategySupport.MATH_CONTEXT);
        return ONE_HUNDRED.subtract(ONE_HUNDRED.divide(
                BigDecimal.ONE.add(relativeStrength), BaselineStrategySupport.MATH_CONTEXT));
    }
}
