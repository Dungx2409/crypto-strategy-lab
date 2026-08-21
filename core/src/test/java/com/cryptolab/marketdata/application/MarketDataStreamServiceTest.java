package com.cryptolab.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.CandleUpdate;
import com.cryptolab.marketdata.domain.MarketDataHealthStatus;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleListener;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.CandleUpdatePublisher;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.marketdata.port.MarketDataScheduler;
import com.cryptolab.marketdata.port.MarketDataTelemetry;
import com.cryptolab.marketdata.port.MarketSubscription;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketDataStreamServiceTest {

    private static final TradingPair PAIR = new TradingPair("BTCUSDT");
    private static final Instant NOW = Instant.parse("2026-08-18T02:00:00Z");

    @Test
    void reconnectsRecoversGapAndRejectsRepeatedCandles() {
        InMemoryStore store = new InMemoryStore();
        Candle duplicate = candle("2026-08-18T01:45:00Z");
        Candle recovered = candle("2026-08-18T01:50:00Z");
        Candle realtime = candle("2026-08-18T01:55:00Z");
        store.saveIfAbsent(duplicate);
        FakeProvider provider = new FakeProvider();
        provider.historical = List.of(duplicate, recovered);
        List<CandleUpdate> published = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler();
        FakeTelemetry telemetry = new FakeTelemetry();
        MarketDataStreamService service = service(provider, store, published::add, scheduler, telemetry);

        MarketStreamRegistration registration = service.open(PAIR, Timeframe.M5);
        provider.listeners.getFirst().onConnected();

        assertThat(service.healthStatus()).isEqualTo(MarketDataHealthStatus.UP);
        assertThat(store.candles).containsExactly(duplicate, recovered);
        assertThat(published).containsExactly(CandleUpdate.closed(recovered));
        assertThat(telemetry.recovered).isEqualTo(1);

        provider.listeners.getFirst().onCandle(CandleUpdate.closed(recovered));
        provider.listeners.getFirst().onCandle(CandleUpdate.closed(realtime));
        assertThat(published).containsExactly(CandleUpdate.closed(recovered), CandleUpdate.closed(realtime));
        assertThat(store.candles).hasSize(3);

        provider.listeners.getFirst().onDisconnected(new IllegalStateException("network lost"));
        assertThat(service.healthStatus()).isEqualTo(MarketDataHealthStatus.DEGRADED);
        assertThat(telemetry.reconnects).isEqualTo(1);
        assertThat(scheduler.delays).containsExactly(Duration.ofSeconds(1));

        scheduler.runLatest();
        assertThat(provider.listeners).hasSize(2);
        provider.listeners.getFirst().onCandle(CandleUpdate.closed(candle("2026-08-18T01:40:00Z")));
        assertThat(store.candles).hasSize(3);
        provider.listeners.get(1).onConnected();
        assertThat(service.healthStatus()).isEqualTo(MarketDataHealthStatus.UP);

        registration.close();
        assertThat(service.activeStreamCount()).isZero();
        assertThat(provider.subscriptions.get(1).active).isFalse();
    }

    @Test
    void publishesInProgressReplacementsAndPersistsOnlyTheFirstClosedCandle() {
        FakeProvider provider = new FakeProvider();
        InMemoryStore store = new InMemoryStore();
        List<CandleUpdate> published = new ArrayList<>();
        FakeTelemetry telemetry = new FakeTelemetry();
        MarketDataStreamService service =
                service(provider, store, published::add, new FakeScheduler(), telemetry);
        Candle firstVersion = candle("2026-08-18T01:50:00Z");
        Candle replacement = new Candle(
                PAIR.symbol(),
                Timeframe.M5,
                firstVersion.openTime(),
                firstVersion.open(),
                new BigDecimal("112"),
                firstVersion.low(),
                new BigDecimal("108"),
                new BigDecimal("14"));
        Candle next = candle("2026-08-18T01:55:00Z");

        service.open(PAIR, Timeframe.M5);
        provider.listeners.getFirst().onCandle(CandleUpdate.inProgress(firstVersion));
        provider.listeners.getFirst().onCandle(CandleUpdate.inProgress(replacement));
        provider.listeners.getFirst().onCandle(CandleUpdate.closed(replacement));
        provider.listeners.getFirst().onCandle(CandleUpdate.closed(replacement));
        provider.listeners.getFirst().onCandle(CandleUpdate.inProgress(next));

        assertThat(published).containsExactly(
                CandleUpdate.inProgress(firstVersion),
                CandleUpdate.inProgress(replacement),
                CandleUpdate.closed(replacement),
                CandleUpdate.inProgress(next));
        assertThat(store.candles).containsExactly(replacement);
    }

    @Test
    void usesBoundedExponentialBackoff() {
        FakeProvider provider = new FakeProvider();
        FakeScheduler scheduler = new FakeScheduler();
        MarketDataStreamService service = service(
                provider,
                new InMemoryStore(),
                ignored -> {},
                scheduler,
                new FakeTelemetry());

        service.open(PAIR, Timeframe.M5);
        provider.listeners.getFirst().onDisconnected(new IllegalStateException("first"));
        scheduler.runLatest();
        provider.listeners.get(1).onDisconnected(new IllegalStateException("second"));
        scheduler.runLatest();
        provider.listeners.get(2).onDisconnected(new IllegalStateException("third"));

        assertThat(scheduler.delays)
                .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void referenceCountsSharedStreamSubscriptions() {
        FakeProvider provider = new FakeProvider();
        MarketDataStreamService service = service(
                provider,
                new InMemoryStore(),
                ignored -> {},
                new FakeScheduler(),
                new FakeTelemetry());

        MarketStreamRegistration first = service.open(PAIR, Timeframe.M15);
        MarketStreamRegistration second = service.open(PAIR, Timeframe.M15);

        assertThat(provider.listeners).hasSize(1);
        first.close();
        assertThat(provider.subscriptions.getFirst().active).isTrue();
        second.close();
        assertThat(provider.subscriptions.getFirst().active).isFalse();
    }

    private MarketDataStreamService service(
            MarketDataProvider provider,
            CandleStore store,
            CandleUpdatePublisher publisher,
            MarketDataScheduler scheduler,
            MarketDataTelemetry telemetry) {
        return new MarketDataStreamService(
                provider,
                store,
                publisher,
                scheduler,
                telemetry,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(1),
                Duration.ofSeconds(4));
    }

    private static Candle candle(String openTime) {
        return new Candle(
                PAIR.symbol(),
                Timeframe.M5,
                Instant.parse(openTime),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("90"),
                new BigDecimal("105"),
                new BigDecimal("10"));
    }

    private static final class FakeProvider implements MarketDataProvider {
        private final List<CandleListener> listeners = new ArrayList<>();
        private final List<FakeSubscription> subscriptions = new ArrayList<>();
        private List<Candle> historical = List.of();

        @Override
        public List<Candle> loadHistorical(
                TradingPair pair, Timeframe timeframe, Instant from, Instant to) {
            return historical;
        }

        @Override
        public MarketSubscription subscribe(
                TradingPair pair, Timeframe timeframe, CandleListener listener) {
            listeners.add(listener);
            FakeSubscription subscription = new FakeSubscription();
            subscriptions.add(subscription);
            return subscription;
        }
    }

    private static final class FakeSubscription implements MarketSubscription {
        private boolean active = true;

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
        }
    }

    private static final class InMemoryStore implements CandleStore {
        private final List<Candle> candles = new ArrayList<>();

        @Override
        public boolean saveIfAbsent(Candle candle) {
            if (candles.stream().anyMatch(existing -> existing.symbol().equals(candle.symbol())
                    && existing.timeframe() == candle.timeframe()
                    && existing.openTime().equals(candle.openTime()))) {
                return false;
            }
            candles.add(candle);
            candles.sort(Comparator.comparing(Candle::openTime));
            return true;
        }

        @Override
        public List<Candle> findLatest(TradingPair pair, Timeframe timeframe, int limit) {
            return List.copyOf(candles);
        }

        @Override
        public Optional<Instant> findLastOpenTime(TradingPair pair, Timeframe timeframe) {
            return candles.stream().map(Candle::openTime).max(Comparator.naturalOrder());
        }
    }

    private static final class FakeScheduler implements MarketDataScheduler {
        private final List<Duration> delays = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public ScheduledTask schedule(Duration delay, Runnable task) {
            delays.add(delay);
            tasks.add(task);
            return () -> tasks.remove(task);
        }

        private void runLatest() {
            tasks.getLast().run();
        }
    }

    private static final class FakeTelemetry implements MarketDataTelemetry {
        private int reconnects;
        private int recovered;

        @Override
        public void reconnectAttempted() {
            reconnects++;
        }

        @Override
        public void gapCandlesRecovered(int count) {
            recovered += count;
        }

        @Override
        public void candlePublished(Duration latency) {}
    }
}
