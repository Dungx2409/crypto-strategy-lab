package com.cryptolab.marketdata.port;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import java.time.Instant;
import java.util.List;

public interface MarketDataProvider {

    List<Candle> loadHistorical(TradingPair pair, Timeframe timeframe, Instant from, Instant to);

    MarketSubscription subscribe(
            TradingPair pair, Timeframe timeframe, CandleListener listener);
}
