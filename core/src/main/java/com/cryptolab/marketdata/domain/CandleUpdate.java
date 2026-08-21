package com.cryptolab.marketdata.domain;

import java.util.Objects;

public record CandleUpdate(Candle candle, boolean closed) {

    public CandleUpdate {
        Objects.requireNonNull(candle, "candle must not be null");
    }

    public static CandleUpdate inProgress(Candle candle) {
        return new CandleUpdate(candle, false);
    }

    public static CandleUpdate closed(Candle candle) {
        return new CandleUpdate(candle, true);
    }
}
