package com.cryptolab.experiment;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static DiscoverySchedule schedule(UUID activeRunId) {
        return new DiscoverySchedule(
                SCHEDULE_ID, UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "BTCUSDT", Timeframe.H1, Duration.ofDays(30), new BigDecimal("10000"),
                20, Duration.ofHours(24), DiscoveryScheduleStatus.ACTIVE, NOW,
                activeRunId, 0, null, NOW, NOW);
    }
}
