package com.cryptolab.marketdata.application;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.MarketDataSnapshot;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.MarketDataProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class MarketDataService {

    private final MarketDataProvider provider;
    private final CandleStore store;
    private final Clock clock;
    private final Set<String> supportedSymbols;
    private final int maximumLimit;

    public MarketDataService(
            MarketDataProvider provider,
            CandleStore store,
            Clock clock,
            Set<String> supportedSymbols,
            int maximumLimit) {
        this.provider = provider;
        this.store = store;
        this.clock = clock;
        this.supportedSymbols = supportedSymbols.stream()
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (maximumLimit < 1) {
            throw new IllegalArgumentException("maximumLimit must be positive");
        }
        this.maximumLimit = maximumLimit;
    }

    public MarketDataSnapshot candles(String symbol, String timeframeCode, int limit) {
        TradingPair pair = validatedPair(symbol);
        Timeframe timeframe = validatedTimeframe(timeframeCode);
        validateLimit(limit);

        Instant to = clock.instant();
        Instant from = to.minus(timeframe.duration().multipliedBy(limit + 1L));
        try {
            List<Candle> historical = provider.loadHistorical(pair, timeframe, from, to);
            historical.forEach(store::saveIfAbsent);
            return new MarketDataSnapshot(pair, timeframe, store.findLatest(pair, timeframe, limit), false);
        } catch (RuntimeException providerFailure) {
            List<Candle> cached = store.findLatest(pair, timeframe, limit);
            if (!cached.isEmpty()) {
                return new MarketDataSnapshot(pair, timeframe, cached, true);
            }
            throw new MarketDataUnavailableException(
                    "Market data provider is unavailable and no cached candles exist", providerFailure);
        }
    }

    public TradingPair validatedPair(String symbol) {
        final TradingPair pair;
        try {
            pair = new TradingPair(symbol);
        } catch (IllegalArgumentException exception) {
            throw new InvalidMarketDataRequestException("INVALID_SYMBOL", exception.getMessage());
        }
        if (!supportedSymbols.contains(pair.symbol())) {
            throw new InvalidMarketDataRequestException(
                    "INVALID_SYMBOL", "Unsupported symbol: " + pair.symbol());
        }
        return pair;
    }

    public Timeframe validatedTimeframe(String timeframeCode) {
        try {
            return Timeframe.fromExchangeCode(timeframeCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidMarketDataRequestException("INVALID_TIMEFRAME", exception.getMessage());
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > maximumLimit) {
            throw new InvalidMarketDataRequestException(
                    "INVALID_LIMIT", "limit must be between 1 and " + maximumLimit);
        }
    }
}
