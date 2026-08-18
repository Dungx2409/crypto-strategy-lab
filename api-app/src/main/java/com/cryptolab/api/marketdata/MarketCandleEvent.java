package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.domain.Candle;
import java.time.Instant;

record MarketCandleEvent(
        String type,
        String symbol,
        String timeframe,
        Instant openTime,
        String open,
        String high,
        String low,
        String close,
        String volume) {

    static MarketCandleEvent closed(Candle candle) {
        return new MarketCandleEvent(
                "CANDLE_CLOSED",
                candle.symbol(),
                candle.timeframe().exchangeCode(),
                candle.openTime(),
                candle.open().toPlainString(),
                candle.high().toPlainString(),
                candle.low().toPlainString(),
                candle.close().toPlainString(),
                candle.volume().toPlainString());
    }
}
