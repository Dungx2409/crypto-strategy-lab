package com.cryptolab.strategy;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.strategy.domain.StrategyContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

final class StrategyTestFixtures {

    static final Instant START = Instant.parse("2026-08-18T00:00:00Z");

    private StrategyTestFixtures() {}

    static StrategyContext context(String... closes) {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            BigDecimal close = new BigDecimal(closes[index]);
            candles.add(candle(index, close, close.add(BigDecimal.TEN), close.subtract(BigDecimal.TEN)));
        }
        return new StrategyContext(
                new TradingPair("BTCUSDT"),
                Timeframe.M5,
                candles,
                START.plus(closes.length * 5L, ChronoUnit.MINUTES));
    }

    static Candle candle(int index, BigDecimal close, BigDecimal high, BigDecimal low) {
        return new Candle(
                "BTCUSDT",
                Timeframe.M5,
                START.plus(index * 5L, ChronoUnit.MINUTES),
                close,
                high,
                low,
                close,
                BigDecimal.ONE);
    }
}
