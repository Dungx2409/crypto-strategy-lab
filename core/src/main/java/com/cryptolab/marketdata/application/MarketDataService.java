package com.cryptolab.marketdata.application;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.MarketDataSnapshot;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.MarketDataProvider;
import java.time.Clock;
import java.time.Duration;
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
    private final int maximumRangeCandles;

    public MarketDataService(
            MarketDataProvider provider,
            CandleStore store,
            Clock clock,
            Set<String> supportedSymbols,
            int maximumLimit) {
        this(provider, store, clock, supportedSymbols, maximumLimit, maximumLimit);
    }

    public MarketDataService(
            MarketDataProvider provider,
            CandleStore store,
            Clock clock,
            Set<String> supportedSymbols,
            int maximumLimit,
            int maximumRangeCandles) {
        this.provider = provider;
        this.store = store;
        this.clock = clock;
        this.supportedSymbols = supportedSymbols.stream()
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (maximumLimit < 1) {
            throw new IllegalArgumentException("maximumLimit must be positive");
        }
        if (maximumRangeCandles < 1) {
            throw new IllegalArgumentException("maximumRangeCandles must be positive");
        }
        this.maximumLimit = maximumLimit;
        this.maximumRangeCandles = maximumRangeCandles;
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

    public MarketDataSnapshot candles(
            String symbol, String timeframeCode, Instant from, Instant to) {
        TradingPair pair = validatedPair(symbol);
        Timeframe timeframe = validatedTimeframe(timeframeCode);
        int expectedCandles = validateRange(from, to, timeframe);
        try {
            List<Candle> historical = provider.loadHistorical(pair, timeframe, from, to);
            historical.forEach(store::saveIfAbsent);
            return new MarketDataSnapshot(pair, timeframe, historical, false);
        } catch (RuntimeException providerFailure) {
            List<Candle> cached = store.findBetween(pair, timeframe, from, to, expectedCandles);
            if (!cached.isEmpty()) {
                return new MarketDataSnapshot(pair, timeframe, cached, true);
            }
            throw new MarketDataUnavailableException(
                    "Market data provider is unavailable and no cached candles exist for the requested range",
                    providerFailure);
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

    private int validateRange(Instant from, Instant to, Timeframe timeframe) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new InvalidMarketDataRequestException(
                    "INVALID_RANGE", "from must be earlier than to");
        }
        long durationSeconds = Duration.between(from, to).getSeconds();
        long candleSeconds = timeframe.duration().getSeconds();
        long expectedCandles = durationSeconds / candleSeconds;
        if (durationSeconds % candleSeconds != 0) {
            expectedCandles++;
        }
        if (expectedCandles > maximumRangeCandles) {
            throw new InvalidMarketDataRequestException(
                    "INVALID_RANGE",
                    "requested range exceeds " + maximumRangeCandles + " candles");
        }
        return Math.toIntExact(expectedCandles);
    }
}
