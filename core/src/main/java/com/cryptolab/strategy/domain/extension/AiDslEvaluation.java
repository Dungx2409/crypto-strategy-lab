package com.cryptolab.strategy.domain.extension;

import com.cryptolab.marketdata.domain.Candle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

final class AiDslEvaluation {

    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final List<Candle> candles;

    AiDslEvaluation(List<Candle> candles) {
        this.candles = List.copyOf(candles);
    }

    BigDecimal latest(AiDslField field) {
        if (candles.isEmpty()) {
            return null;
        }
        return field.value(candles.getLast());
    }

    BigDecimal sma(AiDslField field, int period) {
        if (candles.size() < period) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = candles.size() - period; index < candles.size(); index++) {
            sum = sum.add(field.value(candles.get(index)));
        }
        return sum.divide(BigDecimal.valueOf(period), MATH);
    }

    BigDecimal rsi(int period) {
        if (candles.size() <= period) {
            return null;
        }
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int index = candles.size() - period; index < candles.size(); index++) {
            BigDecimal change = candles.get(index).close().subtract(candles.get(index - 1).close());
            if (change.signum() > 0) {
                gains = gains.add(change);
            } else if (change.signum() < 0) {
                losses = losses.add(change.abs());
            }
        }
        if (gains.signum() == 0 && losses.signum() == 0) {
            return new BigDecimal("50");
        }
        if (losses.signum() == 0) {
            return HUNDRED;
        }
        if (gains.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal relativeStrength = gains.divide(losses, MATH);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(relativeStrength), MATH));
    }

    BigDecimal changePct(AiDslField field, int period) {
        if (candles.size() <= period) {
            return null;
        }
        BigDecimal current = field.value(candles.getLast());
        BigDecimal reference = field.value(candles.get(candles.size() - 1 - period));
        if (reference.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(reference).divide(reference, MATH).multiply(HUNDRED);
    }
}

enum AiDslField {
    OPEN,
    HIGH,
    LOW,
    CLOSE,
    VOLUME;

    BigDecimal value(Candle candle) {
        return switch (this) {
            case OPEN -> candle.open();
            case HIGH -> candle.high();
            case LOW -> candle.low();
            case CLOSE -> candle.close();
            case VOLUME -> candle.volume();
        };
    }
}
