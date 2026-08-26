package com.cryptolab.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.extension.RuleBasedStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RuleBasedStrategyTest {

    @Test
    void executesUserDefinedPriceChangeRules() {
        var strategy = new RuleBasedStrategy(
                RuleBasedStrategy.Metric.PRICE_CHANGE_PCT, 2,
                RuleBasedStrategy.Operator.GT, BigDecimal.ONE,
                RuleBasedStrategy.Metric.PRICE_CHANGE_PCT, 2,
                RuleBasedStrategy.Operator.LT, BigDecimal.ONE.negate());

        assertThat(strategy.analyze(context(List.of("100", "101", "104"))).type())
                .isEqualTo(SignalType.BUY);
        assertThat(strategy.analyze(context(List.of("104", "101", "98"))).type())
                .isEqualTo(SignalType.SELL);
    }

    private static StrategyContext context(List<String> closes) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        var candles = IntStream.range(0, closes.size()).mapToObj(index -> {
            BigDecimal close = new BigDecimal(closes.get(index));
            return new Candle("BTCUSDT", Timeframe.H1, start.plusSeconds(index * 3600L),
                    close, close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), close, BigDecimal.TEN);
        }).toList();
        return new StrategyContext(new TradingPair("BTCUSDT"), Timeframe.H1, candles,
                start.plusSeconds(closes.size() * 3600L));
    }
}
