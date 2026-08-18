package com.cryptolab.strategy.domain.baseline;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.StrategyContext;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

final class BaselineStrategySupport {

    static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

    private BaselineStrategySupport() {}

    static Signal buy(StrategyContext context, String reason) {
        return new Signal(SignalType.BUY, BigDecimal.ONE, context.evaluatedAt(), reason);
    }

    static Signal sell(StrategyContext context, String reason) {
        return new Signal(SignalType.SELL, BigDecimal.ONE.negate(), context.evaluatedAt(), reason);
    }

    static Signal hold(StrategyContext context, String reason) {
        return new Signal(SignalType.HOLD, BigDecimal.ZERO, context.evaluatedAt(), reason);
    }

    static BigDecimal averageClose(List<Candle> candles, int fromInclusive, int toExclusive) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = fromInclusive; index < toExclusive; index++) {
            sum = sum.add(candles.get(index).close());
        }
        return sum.divide(BigDecimal.valueOf(toExclusive - fromInclusive), MATH_CONTEXT);
    }
}
