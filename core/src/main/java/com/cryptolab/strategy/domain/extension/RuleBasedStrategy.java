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

public final class RuleBasedStrategy implements Strategy {

    public static final String TYPE = "RULE";
    public static final String VERSION = "1.0";
    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public enum Metric { RSI, PRICE_CHANGE_PCT, SMA_DISTANCE_PCT, VOLUME_CHANGE_PCT }
    public enum Operator { LT, LTE, GT, GTE }

    private final Rule buy;
    private final Rule sell;

    public RuleBasedStrategy(
            Metric buyMetric, int buyPeriod, Operator buyOperator, BigDecimal buyThreshold,
            Metric sellMetric, int sellPeriod, Operator sellOperator, BigDecimal sellThreshold) {
        this.buy = new Rule(buyMetric, buyPeriod, buyOperator, buyThreshold);
        this.sell = new Rule(sellMetric, sellPeriod, sellOperator, sellThreshold);
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(TYPE, VERSION, Map.of(
                "buyMetric", buy.metric().name(), "buyPeriod", buy.period(),
                "buyOperator", buy.operator().name(), "buyThreshold", buy.threshold(),
                "sellMetric", sell.metric().name(), "sellPeriod", sell.period(),
                "sellOperator", sell.operator().name(), "sellThreshold", sell.threshold()));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        BigDecimal buyValue = value(context.candles(), buy);
        BigDecimal sellValue = value(context.candles(), sell);
        if (buyValue == null || sellValue == null) {
            return signal(context, SignalType.HOLD, BigDecimal.ZERO, "insufficient candles for generated rules");
        }
        if (buy.matches(buyValue)) {
            return signal(context, SignalType.BUY, BigDecimal.ONE,
                    describe("buy", buy, buyValue));
        }
        if (sell.matches(sellValue)) {
            return signal(context, SignalType.SELL, BigDecimal.ONE.negate(),
                    describe("sell", sell, sellValue));
        }
        return signal(context, SignalType.HOLD, BigDecimal.ZERO,
                "generated buy and sell rules did not match");
    }

    private static BigDecimal value(List<Candle> candles, Rule rule) {
        int period = rule.period();
        if (candles.size() < period + (rule.metric() == Metric.RSI ? 1 : 0)) return null;
        Candle latest = candles.get(candles.size() - 1);
        return switch (rule.metric()) {
            case RSI -> rsi(candles, period);
            case PRICE_CHANGE_PCT -> percentChange(
                    latest.close(), candles.get(candles.size() - period).close());
            case VOLUME_CHANGE_PCT -> percentChange(
                    latest.volume(), candles.get(candles.size() - period).volume());
            case SMA_DISTANCE_PCT -> {
                BigDecimal sum = BigDecimal.ZERO;
                for (int index = candles.size() - period; index < candles.size(); index++) {
                    sum = sum.add(candles.get(index).close());
                }
                BigDecimal average = sum.divide(BigDecimal.valueOf(period), MATH);
                yield percentChange(latest.close(), average);
            }
        };
    }

    private static BigDecimal rsi(List<Candle> candles, int period) {
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int index = candles.size() - period; index < candles.size(); index++) {
            BigDecimal change = candles.get(index).close().subtract(candles.get(index - 1).close());
            if (change.signum() > 0) gains = gains.add(change);
            else if (change.signum() < 0) losses = losses.add(change.abs());
        }
        if (gains.signum() == 0 && losses.signum() == 0) return new BigDecimal("50");
        if (losses.signum() == 0) return HUNDRED;
        if (gains.signum() == 0) return BigDecimal.ZERO;
        BigDecimal relativeStrength = gains.divide(losses, MATH);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(relativeStrength), MATH));
    }

    private static BigDecimal percentChange(BigDecimal current, BigDecimal reference) {
        if (reference.signum() == 0) return BigDecimal.ZERO;
        return current.subtract(reference).divide(reference, MATH).multiply(HUNDRED);
    }

    private static String describe(String side, Rule rule, BigDecimal value) {
        return side + " rule matched: " + rule.metric() + "="
                + value.stripTrailingZeros().toPlainString() + " " + rule.operator()
                + " " + rule.threshold().stripTrailingZeros().toPlainString();
    }

    private static Signal signal(
            StrategyContext context, SignalType type, BigDecimal strength, String reason) {
        return new Signal(type, strength, context.evaluatedAt(), reason);
    }

    private record Rule(Metric metric, int period, Operator operator, BigDecimal threshold) {
        private Rule {
            if (metric == null || operator == null || threshold == null) {
                throw new IllegalArgumentException("generated rule fields must not be null");
            }
            if (period < 2 || period > 500) {
                throw new IllegalArgumentException("generated rule period must be between 2 and 500");
            }
        }

        private boolean matches(BigDecimal value) {
            int comparison = value.compareTo(threshold);
            return switch (operator) {
                case LT -> comparison < 0;
                case LTE -> comparison <= 0;
                case GT -> comparison > 0;
                case GTE -> comparison >= 0;
            };
        }
    }
}
