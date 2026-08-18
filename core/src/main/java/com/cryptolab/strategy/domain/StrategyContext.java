package com.cryptolab.strategy.domain;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record StrategyContext(
        TradingPair pair,
        Timeframe timeframe,
        List<Candle> candles,
        Instant evaluatedAt) {

    public StrategyContext {
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        candles = List.copyOf(Objects.requireNonNull(candles, "candles must not be null"));
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");

        Instant previousOpenTime = null;
        for (Candle candle : candles) {
            if (!pair.symbol().equals(candle.symbol())) {
                throw new IllegalArgumentException("all candles must match the strategy trading pair");
            }
            if (timeframe != candle.timeframe()) {
                throw new IllegalArgumentException("all candles must match the strategy timeframe");
            }
            if (previousOpenTime != null && !candle.openTime().isAfter(previousOpenTime)) {
                throw new IllegalArgumentException("candles must be strictly ordered by open time");
            }
            previousOpenTime = candle.openTime();
        }
    }
}
