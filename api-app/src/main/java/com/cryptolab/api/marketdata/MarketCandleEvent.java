package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.domain.CandleUpdate;
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
        String volume,
        boolean closed) {

    static MarketCandleEvent from(CandleUpdate update) {
        var candle = update.candle();
        return new MarketCandleEvent(
                "CANDLE_UPDATE",
                candle.symbol(),
                candle.timeframe().exchangeCode(),
                candle.openTime(),
                candle.open().toPlainString(),
                candle.high().toPlainString(),
                candle.low().toPlainString(),
                candle.close().toPlainString(),
                candle.volume().toPlainString(),
                update.closed());
    }
}
