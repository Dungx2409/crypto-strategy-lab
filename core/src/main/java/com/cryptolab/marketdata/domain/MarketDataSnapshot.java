package com.cryptolab.marketdata.domain;

import java.util.List;

public record MarketDataSnapshot(
        TradingPair pair,
        Timeframe timeframe,
        List<Candle> candles,
        boolean degraded) {

    public MarketDataSnapshot {
        candles = List.copyOf(candles);
    }
}
