package com.cryptolab.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleListener;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.marketdata.port.MarketSubscription;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarketDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T02:00:00Z");

    @Test
    void loadsNormalizesPersistsAndReturnsHistoricalCandles() {
        InMemoryStore store = new InMemoryStore();
        StubProvider provider = new StubProvider(List.of(candle("2026-08-18T01:50:00Z")));
        MarketDataService service = service(provider, store);

        var snapshot = service.candles("btcusdt", "5m", 10);

        assertThat(snapshot.pair().symbol()).isEqualTo("BTCUSDT");
        assertThat(snapshot.timeframe()).isEqualTo(Timeframe.M5);
        assertThat(snapshot.candles()).hasSize(1);
        assertThat(snapshot.degraded()).isFalse();
        assertThat(store.candles).hasSize(1);
    }

    @Test
    void returnsCachedCandlesWithDegradedFlagWhenProviderFails() {
        InMemoryStore store = new InMemoryStore();
        store.saveIfAbsent(candle("2026-08-18T01:50:00Z"));
        StubProvider provider = new StubProvider(List.of());
        provider.failure = new IllegalStateException("exchange unavailable");

        var snapshot = service(provider, store).candles("BTCUSDT", "5m", 10);

        assertThat(snapshot.degraded()).isTrue();
        assertThat(snapshot.candles()).hasSize(1);
    }

    @Test
    void rejectsProviderFailureWithoutCache() {
        StubProvider provider = new StubProvider(List.of());
        provider.failure = new IllegalStateException("exchange unavailable");

        assertThatThrownBy(() -> service(provider, new InMemoryStore())
                        .candles("BTCUSDT", "5m", 10))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("no cached candles");
    }

    @Test
    void validatesSymbolTimeframeAndLimitDeterministically() {
        MarketDataService service = service(new StubProvider(List.of()), new InMemoryStore());

        assertThatThrownBy(() -> service.candles("ETHUSDT", "5m", 10))
                .isInstanceOf(InvalidMarketDataRequestException.class)
                .hasMessage("Unsupported symbol: ETHUSDT");
        assertThatThrownBy(() -> service.candles("BTCUSDT", "2m", 10))
                .isInstanceOf(InvalidMarketDataRequestException.class)
                .hasMessage("Unsupported timeframe: 2m");
        assertThatThrownBy(() -> service.candles("BTCUSDT", "5m", 101))
                .isInstanceOf(InvalidMarketDataRequestException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void loadsTheExactRequestedHistoricalRange() {
        StubProvider provider = new StubProvider(List.of(candle("2025-08-18T01:00:00Z", Timeframe.H1)));
        MarketDataService service = new MarketDataService(
                provider,
                new InMemoryStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of("BTCUSDT"),
                100,
                10_000);
        Instant from = Instant.parse("2025-08-18T00:00:00Z");
        Instant to = Instant.parse("2026-08-18T00:00:00Z");

        var snapshot = service.candles("BTCUSDT", "1h", from, to);

        assertThat(snapshot.candles()).hasSize(1);
        assertThat(provider.requestedFrom).isEqualTo(from);
        assertThat(provider.requestedTo).isEqualTo(to);
    }

    @Test
    void rejectsAnOversizedHistoricalRange() {
        MarketDataService service = new MarketDataService(
                new StubProvider(List.of()),
                new InMemoryStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of("BTCUSDT"),
                100,
                1_000);

        assertThatThrownBy(() -> service.candles(
                        "BTCUSDT", "1h",
                        Instant.parse("2025-08-18T00:00:00Z"),
                        Instant.parse("2026-08-18T00:00:00Z")))
                .isInstanceOf(InvalidMarketDataRequestException.class)
                .hasMessage("requested range exceeds 1000 candles");
    }

    private MarketDataService service(MarketDataProvider provider, CandleStore store) {
        return new MarketDataService(
                provider,
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of("BTCUSDT"),
                100);
    }

    private static Candle candle(String openTime) {
        return candle(openTime, Timeframe.M5);
    }

    private static Candle candle(String openTime, Timeframe timeframe) {
        return new Candle(
                "BTCUSDT",
                timeframe,
                Instant.parse(openTime),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("90"),
                new BigDecimal("105"),
                new BigDecimal("12.5"));
    }

    private static final class StubProvider implements MarketDataProvider {
        private final List<Candle> historical;
        private RuntimeException failure;
        private Instant requestedFrom;
        private Instant requestedTo;

        private StubProvider(List<Candle> historical) {
            this.historical = historical;
        }

        @Override
        public List<Candle> loadHistorical(
                TradingPair pair, Timeframe timeframe, Instant from, Instant to) {
            if (failure != null) {
                throw failure;
            }
            requestedFrom = from;
            requestedTo = to;
            return historical;
        }

        @Override
        public MarketSubscription subscribe(
                TradingPair pair, Timeframe timeframe, CandleListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryStore implements CandleStore {
        private final List<Candle> candles = new ArrayList<>();

        @Override
        public boolean saveIfAbsent(Candle candle) {
            boolean exists = candles.stream().anyMatch(existing -> existing.symbol().equals(candle.symbol())
                    && existing.timeframe() == candle.timeframe()
                    && existing.openTime().equals(candle.openTime()));
            if (!exists) {
                candles.add(candle);
            }
            return !exists;
        }

        @Override
        public List<Candle> findLatest(TradingPair pair, Timeframe timeframe, int limit) {
            List<Candle> matching = candles.stream()
                    .filter(candle -> candle.symbol().equals(pair.symbol()))
                    .filter(candle -> candle.timeframe() == timeframe)
                    .sorted(Comparator.comparing(Candle::openTime))
                    .toList();
            return matching.subList(Math.max(0, matching.size() - limit), matching.size());
        }

        @Override
        public Optional<Instant> findLastOpenTime(TradingPair pair, Timeframe timeframe) {
            return candles.stream().map(Candle::openTime).max(Comparator.naturalOrder());
        }
    }
}
