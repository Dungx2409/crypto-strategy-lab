package com.cryptolab.strategy.domain.extension;

import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.util.Map;

public final class AiDslStrategy implements Strategy {

    public static final String TYPE = "AI_DSL";
    public static final String VERSION = "1.0";
    public static final int MAX_SOURCE_LENGTH = 4_000;

    private final String source;
    private final AiDslBooleanExpression buyRule;
    private final AiDslBooleanExpression sellRule;

    public AiDslStrategy(String source) {
        if (source == null || source.isBlank() || source.length() > MAX_SOURCE_LENGTH) {
            throw new IllegalArgumentException("AI DSL source must contain 1 to 4000 characters");
        }
        this.source = source.trim();

        AiDslParser parser = AiDslParser.parse(this.source);
        buyRule = parser.buyRule();
        sellRule = parser.sellRule();
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(TYPE, VERSION, Map.of("source", source));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        AiDslEvaluation evaluation = new AiDslEvaluation(context.candles().stream()
                .filter(candle -> !candle.openTime().isAfter(context.evaluatedAt()))
                .toList());
        boolean buy = buyRule.evaluate(evaluation) == AiDslTruth.TRUE;
        boolean sell = sellRule.evaluate(evaluation) == AiDslTruth.TRUE;
        if (buy == sell) {
            return new Signal(SignalType.HOLD, BigDecimal.ZERO, context.evaluatedAt(), "AI DSL HOLD");
        }
        return buy
                ? new Signal(SignalType.BUY, BigDecimal.ONE, context.evaluatedAt(), "AI DSL BUY rule matched")
                : new Signal(SignalType.SELL, BigDecimal.ONE.negate(), context.evaluatedAt(),
                        "AI DSL SELL rule matched");
    }
}
