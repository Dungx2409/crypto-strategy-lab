package com.cryptolab.api.experiment;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Instant;

public record ExperimentCandleRequest(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume) {

    Candle toDomain(String symbol, Timeframe timeframe) {
        return new Candle(symbol, timeframe, openTime, open, high, low, close, volume);
    }
}
