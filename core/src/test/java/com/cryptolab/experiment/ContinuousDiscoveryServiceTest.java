package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptolab.experiment.application.ContinuousDiscoveryService;
import com.cryptolab.experiment.application.MarketDatasetService;
import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.domain.DiscoverySchedule;
import com.cryptolab.experiment.domain.DiscoveryScheduleStatus;
import com.cryptolab.experiment.domain.DiscoveryScheduleVersion;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStartCommand;
import com.cryptolab.experiment.port.DiscoveryScheduleRepository;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContinuousDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");
    private static final UUID SCHEDULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SEARCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void claimsDueScheduleAndAlwaysUsesGeneticSearch() {
        DiscoveryScheduleRepository schedules = mock(DiscoveryScheduleRepository.class);
        MarketDataProvider marketData = mock(MarketDataProvider.class);
        MarketDatasetService datasets = mock(MarketDatasetService.class);
        SearchCoordinator searches = mock(SearchCoordinator.class);
        StrategyRegistry strategies = mock(StrategyRegistry.class);
        DiscoverySchedule schedule = schedule(null);
        Candle candle = new Candle(
                "BTCUSDT", Timeframe.H1, NOW.minus(Duration.ofHours(1)),
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"),
                new BigDecimal("105"), BigDecimal.ONE);
        MarketDataset dataset = mock(MarketDataset.class);
        MarketDatasetRef reference = new MarketDatasetRef(
                "BTCUSDT", Timeframe.H1, candle.openTime(), NOW,
                "continuous-v1", "checksum");
        when(schedules.findRunning()).thenReturn(List.of());
        when(schedules.findDue(NOW, 10)).thenReturn(List.of(schedule));
        when(schedules.claim(SCHEDULE_ID, SEARCH_ID, NOW.plus(Duration.ofHours(24)), NOW)).thenReturn(true);
        when(marketData.loadHistorical(any(), eq(Timeframe.H1), any(), eq(NOW))).thenReturn(List.of(candle));
        when(datasets.materialize(eq("BTCUSDT"), eq(Timeframe.H1), eq("continuous-v1"), any()))
                .thenReturn(dataset);
        when(dataset.reference()).thenReturn(reference);
        when(strategies.availableStrategies()).thenReturn(List.of(
                new StrategyPluginDescriptor("MOVING_AVERAGE", "1.0", Map.of())));

        ContinuousDiscoveryService service = new ContinuousDiscoveryService(
                schedules, marketData, datasets, searches, strategies,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> SEARCH_ID);

        service.tick();

        ArgumentCaptor<SearchStartCommand> command = ArgumentCaptor.forClass(SearchStartCommand.class);
        verify(searches).create(command.capture(), eq("genetic"));
        verify(searches).run(command.getValue());
    }

    @Test
    void doesNotLaunchAnotherRunWhileActiveSearchIsRunning() {
        DiscoveryScheduleRepository schedules = mock(DiscoveryScheduleRepository.class);
        SearchCoordinator searches = mock(SearchCoordinator.class);
        SearchRunSummary summary = mock(SearchRunSummary.class);
        SearchRun run = mock(SearchRun.class);
        when(schedules.findRunning()).thenReturn(List.of(schedule(SEARCH_ID)));
        when(searches.details(SEARCH_ID)).thenReturn(summary);
        when(summary.run()).thenReturn(run);
        when(run.status()).thenReturn(SearchRunStatus.RUNNING);
        when(schedules.findDue(NOW, 10)).thenReturn(List.of());
        MarketDataProvider marketData = mock(MarketDataProvider.class);

        ContinuousDiscoveryService service = new ContinuousDiscoveryService(
                schedules, marketData, mock(MarketDatasetService.class), searches,
                mock(StrategyRegistry.class), Clock.fixed(NOW, ZoneOffset.UTC), () -> UUID.randomUUID());

        service.tick();

        verify(marketData, never()).loadHistorical(any(), any(), any(), any());
        verify(schedules, never()).completeRun(any(), any());
    }

    @Test
    void continuesLaunchingDiscoveryRunsAcrossTwentyFourHours() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryScheduleRepository schedules = new InMemoryScheduleRepository(new DiscoverySchedule(
                SCHEDULE_ID, UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "BTCUSDT", Timeframe.H1, Duration.ofDays(30), new BigDecimal("10000"),
                20, Duration.ofHours(1), DiscoveryScheduleStatus.ACTIVE, NOW,
                null, null, 0, null, NOW, NOW));
        MarketDataProvider marketData = mock(MarketDataProvider.class);
        MarketDatasetService datasets = mock(MarketDatasetService.class);
        SearchCoordinator searches = mock(SearchCoordinator.class);
        StrategyRegistry strategies = mock(StrategyRegistry.class);
        AtomicInteger ids = new AtomicInteger();
        Candle first = new Candle(
                "BTCUSDT", Timeframe.H1, NOW.minus(Duration.ofHours(2)),
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"),
                new BigDecimal("105"), BigDecimal.ONE);
        Candle second = new Candle(
                "BTCUSDT", Timeframe.H1, NOW.minus(Duration.ofHours(1)),
                new BigDecimal("105"), new BigDecimal("115"), new BigDecimal("95"),
                new BigDecimal("110"), BigDecimal.ONE);
        MarketDataset dataset = mock(MarketDataset.class);
        MarketDatasetRef reference = new MarketDatasetRef(
                "BTCUSDT", Timeframe.H1, first.openTime(), NOW, "continuous-v1", "checksum");
        SearchRunSummary summary = mock(SearchRunSummary.class);
        SearchRun run = mock(SearchRun.class);
        when(summary.run()).thenReturn(run);
        when(run.status()).thenReturn(SearchRunStatus.COMPLETED);
        when(searches.details(any())).thenReturn(summary);
        when(marketData.loadHistorical(any(), eq(Timeframe.H1), any(), any())).thenReturn(List.of(first, second));
        when(datasets.materialize(eq("BTCUSDT"), eq(Timeframe.H1), eq("continuous-v1"), any()))
                .thenReturn(dataset);
        when(dataset.reference()).thenReturn(reference);
        when(strategies.availableStrategies()).thenReturn(List.of(
                new StrategyPluginDescriptor("MOVING_AVERAGE", "1.0", Map.of())));

        ContinuousDiscoveryService service = new ContinuousDiscoveryService(
                schedules, marketData, datasets, searches, strategies,
                clock, () -> uuid(ids.incrementAndGet()));

        for (int hour = 0; hour <= 24; hour++) {
            service.tick();
            clock.advance(Duration.ofHours(1));
        }

        verify(searches, org.mockito.Mockito.times(25)).run(any(SearchStartCommand.class));
        assertThat(schedules.schedule.completedRuns()).isEqualTo(24);
        assertThat(schedules.schedule.activeSearchRunId()).isNotNull();
        assertThat(schedules.schedule.nextRunAt()).isEqualTo(NOW.plus(Duration.ofHours(25)));
    }

    @Test
    void returnsSavedConfigurationVersionsForAnOwnedSchedule() {
        UUID accountId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        DiscoveryScheduleRepository schedules = mock(DiscoveryScheduleRepository.class);
        DiscoveryScheduleVersion version = new DiscoveryScheduleVersion(
                SCHEDULE_ID, 1, "BTCUSDT", Timeframe.H1, Duration.ofDays(30),
                new BigDecimal("10000"), 20, Duration.ofHours(24), NOW);
        when(schedules.find(accountId, SCHEDULE_ID)).thenReturn(Optional.of(schedule(null)));
        when(schedules.findVersions(accountId, SCHEDULE_ID)).thenReturn(List.of(version));

        ContinuousDiscoveryService service = new ContinuousDiscoveryService(
                schedules, mock(MarketDataProvider.class), mock(MarketDatasetService.class),
                mock(SearchCoordinator.class), mock(StrategyRegistry.class),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> SEARCH_ID);

        assertThat(service.versions(accountId, SCHEDULE_ID)).containsExactly(version);
        verify(schedules).findVersions(accountId, SCHEDULE_ID);
    }

    private static DiscoverySchedule schedule(UUID activeRunId) {
        return new DiscoverySchedule(
                SCHEDULE_ID, UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "BTCUSDT", Timeframe.H1, Duration.ofDays(30), new BigDecimal("10000"),
                20, Duration.ofHours(24), DiscoveryScheduleStatus.ACTIVE, NOW,
                activeRunId, activeRunId, 0, null, NOW, NOW);
    }

    private static UUID uuid(int value) {
        return UUID.fromString("20000000-0000-0000-0000-" + String.format("%012d", value));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    private static final class InMemoryScheduleRepository implements DiscoveryScheduleRepository {
        private DiscoverySchedule schedule;

        private InMemoryScheduleRepository(DiscoverySchedule schedule) {
            this.schedule = schedule;
        }

        @Override
        public DiscoverySchedule create(DiscoverySchedule schedule) {
            this.schedule = schedule;
            return schedule;
        }

        @Override
        public List<DiscoverySchedule> findAll(UUID accountId) {
            return List.of(schedule);
        }

        @Override
        public Optional<DiscoverySchedule> find(UUID accountId, UUID scheduleId) {
            return schedule.id().equals(scheduleId) ? Optional.of(schedule) : Optional.empty();
        }

        @Override
        public List<DiscoverySchedule> findRunning() {
            return schedule.activeSearchRunId() == null ? List.of() : List.of(schedule);
        }

        @Override
        public List<DiscoverySchedule> findDue(Instant now, int limit) {
            if (schedule.status() != DiscoveryScheduleStatus.ACTIVE
                    || schedule.activeSearchRunId() != null
                    || schedule.nextRunAt().isAfter(now)) {
                return List.of();
            }
            return List.of(schedule);
        }

        @Override
        public boolean claim(UUID scheduleId, UUID searchRunId, Instant nextRunAt, Instant updatedAt) {
            if (!schedule.id().equals(scheduleId) || schedule.activeSearchRunId() != null) {
                return false;
            }
            schedule = new DiscoverySchedule(
                    schedule.id(), schedule.accountId(), schedule.symbol(), schedule.timeframe(),
                    schedule.lookback(), schedule.initialCapital(), schedule.candidateLimit(),
                    schedule.interval(), schedule.status(), nextRunAt, searchRunId, searchRunId,
                    schedule.completedRuns(), schedule.lastError(), schedule.createdAt(), updatedAt);
            return true;
        }

        @Override
        public void completeRun(UUID scheduleId, Instant updatedAt) {
            schedule = new DiscoverySchedule(
                    schedule.id(), schedule.accountId(), schedule.symbol(), schedule.timeframe(),
                    schedule.lookback(), schedule.initialCapital(), schedule.candidateLimit(),
                    schedule.interval(), schedule.status(), schedule.nextRunAt(), null,
                    schedule.lastSearchRunId(), schedule.completedRuns() + 1, null, schedule.createdAt(), updatedAt);
        }

        @Override
        public void failRun(UUID scheduleId, String error, Instant updatedAt) {
            schedule = new DiscoverySchedule(
                    schedule.id(), schedule.accountId(), schedule.symbol(), schedule.timeframe(),
                    schedule.lookback(), schedule.initialCapital(), schedule.candidateLimit(),
                    schedule.interval(), schedule.status(), schedule.nextRunAt(), null,
                    schedule.lastSearchRunId(), schedule.completedRuns(), error, schedule.createdAt(), updatedAt);
        }

        @Override
        public boolean stop(UUID accountId, UUID scheduleId, Instant updatedAt) {
            return false;
        }

        @Override
        public boolean start(UUID accountId, UUID scheduleId, Instant nextRunAt, Instant updatedAt) {
            return false;
        }

        @Override
        public DiscoverySchedule updateConfiguration(
                UUID accountId,
                UUID scheduleId,
                String symbol,
                Timeframe timeframe,
                Duration lookback,
                BigDecimal initialCapital,
                long candidateLimit,
                Duration interval,
                Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DiscoveryScheduleVersion> findVersions(UUID accountId, UUID scheduleId) {
            return List.of();
        }

        @Override
        public void recoverInterrupted(Instant now) {}
    }
}
