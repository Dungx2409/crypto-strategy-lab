package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.DiscoverySchedule;
import com.cryptolab.experiment.domain.DiscoveryScheduleStatus;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunKind;
import com.cryptolab.experiment.domain.SearchStartCommand;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.experiment.port.DiscoveryScheduleRepository;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.application.MarketDatasetService;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ContinuousDiscoveryService {

    private final DiscoveryScheduleRepository schedules;
    private final MarketDataProvider marketData;
    private final MarketDatasetService datasets;
    private final SearchCoordinator searches;
    private final StrategyRegistry strategies;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public ContinuousDiscoveryService(
            DiscoveryScheduleRepository schedules,
            MarketDataProvider marketData,
            MarketDatasetService datasets,
            SearchCoordinator searches,
            StrategyRegistry strategies,
            Clock clock,
            Supplier<UUID> ids) {
        this.schedules = schedules;
        this.marketData = marketData;
        this.datasets = datasets;
        this.searches = searches;
        this.strategies = strategies;
        this.clock = clock;
        this.ids = ids;
    }

    public DiscoverySchedule create(
            UUID accountId,
            String symbol,
            Timeframe timeframe,
            Duration lookback,
            BigDecimal initialCapital,
            long candidateLimit,
            Duration interval) {
        Instant now = clock.instant();
        return schedules.create(new DiscoverySchedule(
                ids.get(), accountId, symbol, timeframe, lookback, initialCapital,
                candidateLimit, interval, DiscoveryScheduleStatus.ACTIVE, now,
                null, 0, null, now, now));
    }

    public java.util.List<DiscoverySchedule> list(UUID accountId) {
        return schedules.findAll(accountId);
    }

    public DiscoverySchedule get(UUID accountId, UUID scheduleId) {
        return schedules.find(accountId, scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Discovery schedule was not found: " + scheduleId));
    }

    public DiscoverySchedule stop(UUID accountId, UUID scheduleId) {
        DiscoverySchedule schedule = get(accountId, scheduleId);
        if (schedule.activeSearchRunId() != null) {
            searches.cancel(schedule.activeSearchRunId());
        }
        schedules.stop(accountId, scheduleId, clock.instant());
        return get(accountId, scheduleId);
    }

    public DiscoverySchedule start(UUID accountId, UUID scheduleId) {
        get(accountId, scheduleId);
        schedules.start(accountId, scheduleId, clock.instant(), clock.instant());
        return get(accountId, scheduleId);
    }

    public void recoverInterruptedRuns() {
        schedules.recoverInterrupted(clock.instant());
    }

    public void tick() {
        finishTerminalRuns();
        Instant now = clock.instant();
        for (DiscoverySchedule schedule : schedules.findDue(now, 10)) {
            UUID searchRunId = ids.get();
            if (!schedules.claim(schedule.id(), searchRunId, now.plus(schedule.interval()), now)) {
                continue;
            }
            try {
                SearchStartCommand command = command(schedule, searchRunId, now);
                searches.create(command, GeneticStrategyGenerator.TYPE);
                searches.run(command);
            } catch (RuntimeException failure) {
                schedules.failRun(schedule.id(), safeMessage(failure), clock.instant());
            }
        }
    }

    private void finishTerminalRuns() {
        for (DiscoverySchedule schedule : schedules.findRunning()) {
            try {
                SearchRunStatus status = searches.details(schedule.activeSearchRunId()).run().status();
                if (status == SearchRunStatus.COMPLETED) {
                    schedules.completeRun(schedule.id(), clock.instant());
                } else if (status == SearchRunStatus.CANCELLED || status == SearchRunStatus.FAILED) {
                    schedules.failRun(schedule.id(), "Search run ended with status " + status, clock.instant());
                }
            } catch (RuntimeException failure) {
                schedules.failRun(schedule.id(), safeMessage(failure), clock.instant());
            }
        }
    }

    private SearchStartCommand command(DiscoverySchedule schedule, UUID searchRunId, Instant now) {
        TradingPair pair = new TradingPair(schedule.symbol());
        var candles = marketData.loadHistorical(
                pair, schedule.timeframe(), now.minus(schedule.lookback()), now);
        var dataset = datasets.materialize(
                schedule.symbol(), schedule.timeframe(), "continuous-v1", candles).reference();
        var descriptors = strategies.availableStrategies();
        var strategyTypes = descriptors.stream().map(item -> item.type()).toList();
        Map<String, String> versions = descriptors.stream().collect(Collectors.toMap(
                item -> item.type(), item -> item.version(), (left, right) -> left));
        SearchContext context = new SearchContext(
                searchRunId, dataset, strategyTypes, versions, new SearchParameterSpace(Map.of()),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                searchRunId.getMostSignificantBits() ^ searchRunId.getLeastSignificantBits(),
                new StopConditions(schedule.candidateLimit(), null, null),
                (int) Math.min(100, schedule.candidateLimit()));
        ExecutionConfig execution = new ExecutionConfig(
                schedule.initialCapital(), new BigDecimal("0.001"), false,
                DeterministicBacktestEngine.FILL_POLICY, DeterministicBacktestEngine.VERSION);
        return new SearchStartCommand(
                context, execution, schedule.accountId(), SearchRunKind.DISCOVERY);
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
