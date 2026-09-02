package com.cryptolab.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.extension.AiDslStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AiDslStrategyTest {

    @Test
    void executesIndicatorsAndBooleanPrecedenceDeterministically() {
        AiDslStrategy strategy = new AiDslStrategy("""
                BUY WHEN CLOSE > SMA(CLOSE, 3) OR CHANGE_PCT(VOLUME, 2) < 0 AND RSI(2) < 50
                SELL WHEN CLOSE < SMA(CLOSE, 3)
                """);
        StrategyContext context = context(
                List.of("100", "101", "104"), List.of("10", "10", "12"));

        var signal = strategy.analyze(context);

        assertThat(signal.type()).isEqualTo(SignalType.BUY);
        assertThat(signal.at()).isEqualTo(context.evaluatedAt());
        assertThat(strategy.descriptor().parameters().get("source").toString())
                .contains("BUY WHEN", "SELL WHEN");
    }

    @Test
    void returnsHoldWhenHistoryIsMissingEvenUnderNot() {
        AiDslStrategy strategy = new AiDslStrategy("""
                BUY WHEN NOT RSI(14) < 30
                SELL WHEN CHANGE_PCT(CLOSE, 10) < -5
                """);

        assertThat(strategy.analyze(context(List.of("100", "101"), List.of("10", "10"))).type())
                .isEqualTo(SignalType.HOLD);
    }

    @Test
    void returnsHoldWhenBuyAndSellRulesBothMatch() {
        AiDslStrategy strategy = new AiDslStrategy("""
                BUY WHEN CLOSE >= 100
                SELL WHEN CLOSE <= 100
                """);

        assertThat(strategy.analyze(context(List.of("100"), List.of("10"))).type())
                .isEqualTo(SignalType.HOLD);
    }

    @Test
    void ignoresCandlesAfterTheEvaluationTime() {
        AiDslStrategy strategy = new AiDslStrategy("""
                BUY WHEN CLOSE == 100
                SELL WHEN CLOSE == 200
                """);
        StrategyContext contextWithFuture = context(
                List.of("100", "200"), List.of("10", "10"));
        StrategyContext cutoff = new StrategyContext(
                contextWithFuture.pair(),
                contextWithFuture.timeframe(),
                contextWithFuture.candles(),
                contextWithFuture.candles().getFirst().openTime().plusSeconds(3599));

        assertThat(strategy.analyze(cutoff).type()).isEqualTo(SignalType.BUY);
    }

    @Test
    void rejectsUnknownCapabilitiesAndUnboundedPrograms() {
        assertThatThrownBy(() -> new AiDslStrategy("""
                BUY WHEN FILE_READ('/etc/passwd') == 1
                SELL WHEN CLOSE < 0
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported AI DSL word");
        assertThatThrownBy(() -> new AiDslStrategy("""
                BUY WHEN SMA(CLOSE, 501) > CLOSE
                SELL WHEN CLOSE < 0
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period must be between 2 and 500");
        assertThatThrownBy(() -> new AiDslStrategy("x".repeat(4_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 to 4000");

        String tooManyTokens = "BUY WHEN " + "NOT ".repeat(257)
                + "CLOSE > 0 SELL WHEN CLOSE < 0";
        assertThatThrownBy(() -> new AiDslStrategy(tooManyTokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 256 tokens");

        String tooManyNodes = "BUY WHEN "
                + String.join(" AND ", java.util.Collections.nCopies(33, "CLOSE > 0"))
                + " SELL WHEN CLOSE < 0";
        assertThatThrownBy(() -> new AiDslStrategy(tooManyNodes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 128 expression nodes");
    }

    private static StrategyContext context(List<String> closes, List<String> volumes) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<Candle> candles = IntStream.range(0, closes.size()).mapToObj(index -> {
            BigDecimal close = new BigDecimal(closes.get(index));
            return new Candle(
                    "BTCUSDT",
                    Timeframe.H1,
                    start.plusSeconds(index * 3600L),
                    close,
                    close.add(BigDecimal.ONE),
                    close.subtract(BigDecimal.ONE),
                    close,
                    new BigDecimal(volumes.get(index)));
        }).toList();
        return new StrategyContext(
                new TradingPair("BTCUSDT"),
                Timeframe.H1,
                candles,
                start.plusSeconds(closes.size() * 3600L));
    }
}
