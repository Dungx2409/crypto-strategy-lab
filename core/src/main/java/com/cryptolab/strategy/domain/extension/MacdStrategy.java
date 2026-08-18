package com.cryptolab.strategy.domain.extension;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Deterministic MACD crossover strategy added as the M7 strategy-extension proof.
 *
 * <p>Both price EMAs and the MACD signal EMA are seeded with their first available
 * value. Analysis consumes only the ordered candle prefix supplied by the caller,
 * so the strategy cannot read future candles.
 */
public final class MacdStrategy implements Strategy {

    public static final String TYPE = "MACD";
    public static final String VERSION = "1.0";

    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;

    public MacdStrategy(int fastPeriod, int slowPeriod, int signalPeriod) {
        if (fastPeriod < 1) {
            throw new IllegalArgumentException("fastPeriod must be positive");
        }
        if (slowPeriod <= fastPeriod) {
            throw new IllegalArgumentException("slowPeriod must be greater than fastPeriod");
        }
        if (signalPeriod < 1) {
            throw new IllegalArgumentException("signalPeriod must be positive");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(
                TYPE,
                VERSION,
                Map.of(
                        "fastPeriod", fastPeriod,
                        "slowPeriod", slowPeriod,
                        "signalPeriod", signalPeriod));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        List<Candle> candles = context.candles();
        if (candles.size() < slowPeriod + signalPeriod + 1) {
            return signal(context, SignalType.HOLD, BigDecimal.ZERO, "insufficient candles for MACD crossover");
        }

        BigDecimal fastAlpha = alpha(fastPeriod);
        BigDecimal slowAlpha = alpha(slowPeriod);
        BigDecimal signalAlpha = alpha(signalPeriod);
        BigDecimal fastEma = candles.getFirst().close();
        BigDecimal slowEma = fastEma;
        BigDecimal macd = BigDecimal.ZERO;
        BigDecimal signalEma = BigDecimal.ZERO;
        BigDecimal previousMacd = macd;
        BigDecimal previousSignalEma = signalEma;

        for (int index = 1; index < candles.size(); index++) {
            previousMacd = macd;
            previousSignalEma = signalEma;
            BigDecimal close = candles.get(index).close();
            fastEma = ema(close, fastEma, fastAlpha);
            slowEma = ema(close, slowEma, slowAlpha);
            macd = fastEma.subtract(slowEma, MATH_CONTEXT);
            signalEma = ema(macd, signalEma, signalAlpha);
        }

        if (previousMacd.compareTo(previousSignalEma) <= 0 && macd.compareTo(signalEma) > 0) {
            return signal(context, SignalType.BUY, BigDecimal.ONE, "MACD crossed above its signal line");
        }
        if (previousMacd.compareTo(previousSignalEma) >= 0 && macd.compareTo(signalEma) < 0) {
            return signal(context, SignalType.SELL, BigDecimal.ONE.negate(), "MACD crossed below its signal line");
        }
        return signal(context, SignalType.HOLD, BigDecimal.ZERO, "no MACD crossover");
    }

    private static BigDecimal alpha(int period) {
        return TWO.divide(BigDecimal.valueOf(period + 1L), MATH_CONTEXT);
    }

    private static BigDecimal ema(BigDecimal value, BigDecimal previous, BigDecimal alpha) {
        return value.multiply(alpha, MATH_CONTEXT)
                .add(previous.multiply(BigDecimal.ONE.subtract(alpha, MATH_CONTEXT), MATH_CONTEXT), MATH_CONTEXT);
    }

    private static Signal signal(
            StrategyContext context,
            SignalType type,
            BigDecimal strength,
            String reason) {
        return new Signal(type, strength, context.evaluatedAt(), reason);
    }
}
