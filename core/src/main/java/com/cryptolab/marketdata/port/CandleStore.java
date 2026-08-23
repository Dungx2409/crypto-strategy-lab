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

    default List<Candle> findBetween(
            TradingPair pair, Timeframe timeframe, Instant from, Instant to, int limit) {
        return findLatest(pair, timeframe, limit).stream()
                .filter(candle -> !candle.openTime().isBefore(from))
                .filter(candle -> candle.openTime().isBefore(to))
                .toList();
    }

    Optional<Instant> findLastOpenTime(TradingPair pair, Timeframe timeframe);
}
