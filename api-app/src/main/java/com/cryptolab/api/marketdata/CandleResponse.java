package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.domain.Candle;
import java.time.Instant;

record CandleResponse(
        Instant openTime,
        String open,
        String high,
        String low,
        String close,
        String volume) {

    static CandleResponse from(Candle candle) {
        return new CandleResponse(
                candle.openTime(),
                candle.open().toPlainString(),
                candle.high().toPlainString(),
                candle.low().toPlainString(),
                candle.close().toPlainString(),
                candle.volume().toPlainString());
    }
}
