package com.cryptolab.marketdata.port;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import java.time.Instant;
import java.util.List;

public interface MarketDataPort {
    List<Candle> candles(TradingPair pair, Timeframe timeframe, Instant from, Instant to);
}
