package com.cryptolab.marketdata.application;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MarketDataStreamService implements AutoCloseable {

    private final MarketDataProvider provider;
    private final CandleStore store;
    private final CandleUpdatePublisher publisher;
    private final MarketDataScheduler scheduler;
    private final MarketDataTelemetry telemetry;
    private final Clock clock;
    private final Duration initialReconnectDelay;
    private final Duration maximumReconnectDelay;
    private final Map<StreamKey, StreamState> streams = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public MarketDataStreamService(
            MarketDataProvider provider,
            CandleStore store,
            CandleUpdatePublisher publisher,
            MarketDataScheduler scheduler,
            MarketDataTelemetry telemetry,
            Clock clock,
            Duration initialReconnectDelay,
            Duration maximumReconnectDelay) {
        if (initialReconnectDelay.isNegative()
                || initialReconnectDelay.isZero()
                || maximumReconnectDelay.compareTo(initialReconnectDelay) < 0) {
            throw new IllegalArgumentException("Reconnect delays are invalid");
        }
        this.provider = provider;
        this.store = store;
        this.publisher = publisher;
        this.scheduler = scheduler;
        this.telemetry = telemetry;
        this.clock = clock;
        this.initialReconnectDelay = initialReconnectDelay;
        this.maximumReconnectDelay = maximumReconnectDelay;
    }

    public MarketStreamRegistration open(TradingPair pair, Timeframe timeframe) {
        if (closed.get()) {
            throw new IllegalStateException("Market data stream service is closed");
        }
        StreamKey key = new StreamKey(pair, timeframe);
        StreamState state = streams.compute(key, (ignored, existing) -> {
            StreamState target = existing == null ? new StreamState(key, initialReconnectDelay) : existing;
            target.references++;
            return target;
        });
        synchronized (state) {
            if (!state.started) {
                state.started = true;
                connect(state);
            }
        }
        AtomicBoolean registrationClosed = new AtomicBoolean();
        return () -> {
            if (registrationClosed.compareAndSet(false, true)) {
                release(key, state);
            }
        };
    }

    public MarketDataHealthStatus healthStatus() {
        if (closed.get()) {
            return MarketDataHealthStatus.DOWN;
        }
        return streams.values().stream()
                .map(state -> state.status)
                .max(Comparator.comparingInt(MarketDataStreamService::severity))
                .orElse(MarketDataHealthStatus.UP);
    }

    public int activeStreamCount() {
        return streams.size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        streams.values().forEach(this::stop);
        streams.clear();
    }

    private void connect(StreamState state) {
        if (closed.get() || state.references == 0) {
            return;
        }
        state.status = MarketDataHealthStatus.DEGRADED;
        long generation = ++state.generation;
        try {
            state.subscription = provider.subscribe(
                    state.key.pair(), state.key.timeframe(), new CandleListener() {
                        @Override
                        public void onCandle(CandleUpdate update) {
                            acceptRealtime(state, generation, update);
                        }

                        @Override
                        public void onConnected() {
                            connected(state, generation);
                        }

                        @Override
                        public void onDisconnected(Throwable cause) {
                            disconnected(state, generation);
                        }
                    });
        } catch (RuntimeException connectionFailure) {
            disconnected(state, generation);
        }
    }

    private void connected(StreamState state, long generation) {
        synchronized (state) {
            if (!isCurrent(state, generation)) {
                return;
            }
            try {
                recoverGap(state);
                state.nextReconnectDelay = initialReconnectDelay;
                state.status = MarketDataHealthStatus.UP;
            } catch (RuntimeException recoveryFailure) {
                disconnected(state, generation);
            }
        }
    }

    private void recoverGap(StreamState state) {
        store.findLastOpenTime(state.key.pair(), state.key.timeframe()).ifPresent(lastOpenTime -> {
            Instant now = clock.instant();
            if (lastOpenTime.isAfter(now)) {
                return;
            }
            int recovered = 0;
            for (Candle candle : provider.loadHistorical(
                    state.key.pair(), state.key.timeframe(), lastOpenTime, now)) {
                if (store.saveIfAbsent(candle)) {
                    recovered++;
                    publisher.publish(CandleUpdate.closed(candle));
                    recordLatency(candle);
                }
            }
            telemetry.gapCandlesRecovered(recovered);
        });
    }

    private void acceptRealtime(StreamState state, long generation, CandleUpdate update) {
        synchronized (state) {
            if (!isCurrent(state, generation)) {
                return;
            }
            if (!update.closed()) {
                publisher.publish(update);
                return;
            }
            Candle candle = update.candle();
            if (store.saveIfAbsent(candle)) {
                publisher.publish(update);
                recordLatency(candle);
            }
        }
    }

    private void disconnected(StreamState state, long generation) {
        synchronized (state) {
            if (!isCurrent(state, generation)) {
                return;
            }
            if (state.reconnectTask != null) {
                return;
            }
            state.status = MarketDataHealthStatus.DEGRADED;
            telemetry.reconnectAttempted();
            Duration delay = state.nextReconnectDelay;
            state.nextReconnectDelay = boundedDouble(delay);
            state.reconnectTask = scheduler.schedule(delay, () -> {
                synchronized (state) {
                    if (isCurrent(state, generation)) {
                        state.reconnectTask = null;
                        connect(state);
                    }
                }
            });
        }
    }

    private void release(StreamKey key, StreamState state) {
        synchronized (state) {
            if (state.references > 0) {
                state.references--;
            }
            if (state.references == 0) {
                streams.remove(key, state);
                stop(state);
            }
        }
    }

    private void stop(StreamState state) {
        synchronized (state) {
            state.generation++;
            if (state.reconnectTask != null) {
                state.reconnectTask.cancel();
                state.reconnectTask = null;
            }
            if (state.subscription != null) {
                state.subscription.close();
                state.subscription = null;
            }
            state.status = MarketDataHealthStatus.DOWN;
            state.started = false;
        }
    }

    private boolean isCurrent(StreamState state, long generation) {
        return !closed.get() && state.references > 0 && state.generation == generation;
    }

    private Duration boundedDouble(Duration current) {
        try {
            Duration doubled = current.multipliedBy(2);
            return doubled.compareTo(maximumReconnectDelay) > 0 ? maximumReconnectDelay : doubled;
        } catch (ArithmeticException overflow) {
            return maximumReconnectDelay;
        }
    }

    private void recordLatency(Candle candle) {
        Duration latency = Duration.between(
                candle.openTime().plus(candle.timeframe().duration()), clock.instant());
        telemetry.candlePublished(latency.isNegative() ? Duration.ZERO : latency);
    }

    private static int severity(MarketDataHealthStatus status) {
        return switch (status) {
            case UP -> 0;
            case DEGRADED -> 1;
            case DOWN -> 2;
        };
    }

    private record StreamKey(TradingPair pair, Timeframe timeframe) {}

    private static final class StreamState {
        private final StreamKey key;
        private int references;
        private boolean started;
        private long generation;
        private volatile MarketDataHealthStatus status = MarketDataHealthStatus.DEGRADED;
        private Duration nextReconnectDelay;
        private MarketSubscription subscription;
        private MarketDataScheduler.ScheduledTask reconnectTask;

        private StreamState(StreamKey key, Duration initialReconnectDelay) {
            this.key = key;
            this.nextReconnectDelay = initialReconnectDelay;
        }
    }
}
