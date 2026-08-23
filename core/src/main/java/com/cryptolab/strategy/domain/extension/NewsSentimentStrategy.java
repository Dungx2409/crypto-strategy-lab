package com.cryptolab.strategy.domain.extension;

import com.cryptolab.shared.domain.SentimentObservation;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class NewsSentimentStrategy implements Strategy {

    public static final String TYPE = "NEWS_SENTIMENT";
    public static final String VERSION = "1.0";
    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_EVEN);

    private final int windowMinutes;
    private final BigDecimal buyThreshold;
    private final BigDecimal sellThreshold;

    public NewsSentimentStrategy(
            int windowMinutes,
            BigDecimal buyThreshold,
            BigDecimal sellThreshold) {
        if (windowMinutes < 1) {
            throw new IllegalArgumentException("windowMinutes must be positive");
        }
        if (buyThreshold == null
                || buyThreshold.signum() <= 0
                || buyThreshold.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("buyThreshold must be in (0, 1]");
        }
        if (sellThreshold == null
                || sellThreshold.compareTo(BigDecimal.ONE.negate()) < 0
                || sellThreshold.signum() >= 0) {
            throw new IllegalArgumentException("sellThreshold must be in [-1, 0)");
        }
        this.windowMinutes = windowMinutes;
        this.buyThreshold = buyThreshold;
        this.sellThreshold = sellThreshold;
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(
                TYPE,
                VERSION,
                Map.of(
                        "windowMinutes", windowMinutes,
                        "buyThreshold", buyThreshold,
                        "sellThreshold", sellThreshold));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        Instant from = context.evaluatedAt().minus(Duration.ofMinutes(windowMinutes));
        List<SentimentObservation> observations = context.sentimentObservations().stream()
                .filter(observation -> !observation.observedAt().isBefore(from))
                .toList();
        if (observations.isEmpty()) {
            return signal(context, SignalType.HOLD, BigDecimal.ZERO, "no sentiment in configured window");
        }
        BigDecimal average = observations.stream()
                .map(SentimentObservation::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(observations.size()), MATH_CONTEXT);
        if (average.compareTo(buyThreshold) > 0) {
            return signal(context, SignalType.BUY, average, "average news sentiment above buy threshold");
        }
        if (average.compareTo(sellThreshold) < 0) {
            return signal(context, SignalType.SELL, average, "average news sentiment below sell threshold");
        }
        return signal(context, SignalType.HOLD, average, "average news sentiment inside neutral range");
    }

    private static Signal signal(
            StrategyContext context,
            SignalType type,
            BigDecimal strength,
            String reason) {
        return new Signal(type, strength, context.evaluatedAt(), reason);
    }
}
