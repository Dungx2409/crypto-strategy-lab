package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.domain.MarketDataSnapshot;
import java.util.List;

record MarketCandlesResponse(
        String symbol,
        String timeframe,
        List<CandleResponse> candles,
        boolean degraded) {

    static MarketCandlesResponse from(MarketDataSnapshot snapshot) {
        return new MarketCandlesResponse(
                snapshot.pair().symbol(),
                snapshot.timeframe().exchangeCode(),
                snapshot.candles().stream().map(CandleResponse::from).toList(),
                snapshot.degraded());
    }
}
