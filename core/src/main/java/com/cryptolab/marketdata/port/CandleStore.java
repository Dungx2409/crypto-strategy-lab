package com.cryptolab.marketdata.port;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CandleStore {

    boolean saveIfAbsent(Candle candle);

    List<Candle> findLatest(TradingPair pair, Timeframe timeframe, int limit);

    Optional<Instant> findLastOpenTime(TradingPair pair, Timeframe timeframe);
}
