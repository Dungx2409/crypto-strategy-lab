package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.application.ExperimentPlanFactory;
import com.cryptolab.experiment.application.ManualRunService;
import com.cryptolab.experiment.domain.Experiment;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ManualRunBatch;
import com.cryptolab.experiment.domain.ManualRunChild;
import com.cryptolab.experiment.domain.ManualRunStatus;
import com.cryptolab.experiment.port.ManualRunRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.UserStrategy;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManualRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void rejectsDuplicateTimeframesAndRangesLongerThanOneYear() {
        ManualRunService service = service(
                mock(ManualRunRepository.class),
                mock(UserStrategyRepository.class),
                mock(MarketDataProvider.class),
                mock(ExperimentPlanFactory.class),
                mock(ExperimentPipelineService.class));

        assertThatThrownBy(() -> service.create(
                        UUID.randomUUID(), UUID.randomUUID(), "BTCUSDT",
                        List.of(Timeframe.M5, Timeframe.M5), NOW.minusSeconds(3600), NOW,
                        ExperimentTestFixtures.executionConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique timeframes");
        assertThatThrownBy(() -> service.create(
                        UUID.randomUUID(), UUID.randomUUID(), "BTCUSDT",
                        List.of(Timeframe.M5), NOW.minusSeconds(367L * 86_400L), NOW,
                        ExperimentTestFixtures.executionConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366 days");
    }

    @Test
    void restartRecoveryOnlyExecutesUnfinishedChildren() {
        ManualRunRepository batches = mock(ManualRunRepository.class);
        UserStrategyRepository strategies = mock(UserStrategyRepository.class);
        MarketDataProvider marketData = mock(MarketDataProvider.class);
        ExperimentPlanFactory plans = mock(ExperimentPlanFactory.class);
        ExperimentPipelineService pipeline = mock(ExperimentPipelineService.class);
        ManualRunService service = service(batches, strategies, marketData, plans, pipeline);
        UUID accountId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID completedChild = UUID.randomUUID();
        UUID unfinishedChild = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        ManualRunBatch batch = new ManualRunBatch(
                batchId,
                accountId,
                strategyId,
                "BTCUSDT",
                NOW.minusSeconds(3600),
                NOW,
                ExperimentTestFixtures.executionConfig(),
                ManualRunStatus.RUNNING,
                false,
                NOW.minusSeconds(60),
                NOW.minusSeconds(30),
                List.of(
                        new ManualRunChild(
                                completedChild, Timeframe.M5, ManualRunStatus.COMPLETED,
                                UUID.randomUUID(), null),
                        new ManualRunChild(
                                unfinishedChild, Timeframe.M15, ManualRunStatus.PREPARING,
                                null, null)));
        when(batches.find(batchId)).thenReturn(Optional.of(batch));
        when(strategies.find(accountId, strategyId)).thenReturn(Optional.of(strategy(accountId, strategyId)));
        when(marketData.loadHistorical(any(), any(), any(), any()))
                .thenReturn(ExperimentTestFixtures.dataset().candles());
        ExperimentPlan plan = mock(ExperimentPlan.class);
        when(plans.create(any())).thenReturn(plan);
        ExperimentDetails details = mock(ExperimentDetails.class);
        Experiment experiment = mock(Experiment.class);
        when(details.experiment()).thenReturn(experiment);
        when(experiment.id()).thenReturn(experimentId);
        when(pipeline.execute(plan)).thenReturn(details);

        service.execute(batchId);

        verify(marketData).loadHistorical(any(), any(), any(), any());
        verify(batches).completeChild(unfinishedChild, experimentId, NOW);
        verify(batches, never()).completeChild(completedChild, experimentId, NOW);
        verify(batches).finish(batchId, NOW);
    }

    private static ManualRunService service(
            ManualRunRepository batches,
            UserStrategyRepository strategies,
            MarketDataProvider marketData,
            ExperimentPlanFactory plans,
            ExperimentPipelineService pipeline) {
        return new ManualRunService(
                batches,
                strategies,
                marketData,
                plans,
                pipeline,
                Clock.fixed(NOW, ZoneOffset.UTC),
                UUID::randomUUID);
    }

    private static UserStrategy strategy(UUID accountId, UUID strategyId) {
        return new UserStrategy(
                strategyId,
                accountId,
                1,
                new UserStrategyDocument(
                        "Manual MA",
                        "",
                        List.of(new StrategyDefinition(
                                "MOVING_AVERAGE", "1.0",
                                Map.of("fastPeriod", 10, "slowPeriod", 20))),
                        new CombinationPolicyDefinition(
                                "MAJORITY", "1.0", Map.of(), BigDecimal.ZERO)),
                "manual",
                NOW);
    }
}
