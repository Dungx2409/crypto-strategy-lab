package com.cryptolab.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.shared.domain.SentimentObservation;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.extension.NewsSentimentStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsSentimentStrategyTest {

    private static final Instant AT = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void emitsBuyAndSellFromTimeSafeWindowAverages() {
        var strategy = new NewsSentimentStrategy(60, new BigDecimal("0.5"), new BigDecimal("-0.5"));

        assertThat(strategy.analyze(context(observation("positive", "0.8", 30))).type())
                .isEqualTo(SignalType.BUY);
        assertThat(strategy.analyze(context(observation("negative", "-0.8", 30))).type())
                .isEqualTo(SignalType.SELL);
        assertThat(strategy.analyze(context(observation("old", "0.9", 90))).type())
                .isEqualTo(SignalType.HOLD);
    }

    private static StrategyContext context(SentimentObservation observation) {
        return new StrategyContext(
                new TradingPair("BTCUSDT"),
                Timeframe.M5,
                List.of(StrategyTestFixtures.candle(0, new BigDecimal("100"),
                        new BigDecimal("110"), new BigDecimal("90"))),
                AT,
                List.of(observation));
    }

    private static SentimentObservation observation(String id, String score, int minutesAgo) {
        return new SentimentObservation(
                id,
                AT.minusSeconds(minutesAgo * 60L),
                new BigDecimal(score),
                "keyword",
                "1.0",
                "news-v1",
                "normalize-v1");
    }
}
