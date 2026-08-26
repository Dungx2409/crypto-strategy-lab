package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.experiment.domain.ManualRunBatch;
import com.cryptolab.experiment.domain.ManualRunChild;
import com.cryptolab.experiment.domain.ManualRunStatus;
import com.cryptolab.experiment.domain.SingleExperimentCommand;
import com.cryptolab.experiment.port.ManualRunRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ManualRunService {

    private static final Duration MAX_RANGE = Duration.ofDays(366);

    private final ManualRunRepository batches;
    private final UserStrategyRepository strategies;
    private final MarketDataProvider marketData;
    private final ExperimentPlanFactory plans;
    private final ExperimentPipelineService pipeline;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public ManualRunService(
            ManualRunRepository batches,
            UserStrategyRepository strategies,
            MarketDataProvider marketData,
            ExperimentPlanFactory plans,
            ExperimentPipelineService pipeline,
            Clock clock,
            Supplier<UUID> ids) {
        this.batches = Objects.requireNonNull(batches);
        this.strategies = Objects.requireNonNull(strategies);
        this.marketData = Objects.requireNonNull(marketData);
        this.plans = Objects.requireNonNull(plans);
        this.pipeline = Objects.requireNonNull(pipeline);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    public ManualRunBatch create(
            UUID accountId,
            UUID strategyId,
            String symbol,
            List<Timeframe> timeframes,
            Instant from,
            Instant to,
            ExecutionConfig executionConfig) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("manual run range must not exceed 366 days");
        }
        List<Timeframe> unique = new ArrayList<>(new LinkedHashSet<>(timeframes));
        if (unique.isEmpty() || unique.size() > 4 || unique.size() != timeframes.size()) {
            throw new IllegalArgumentException("manual runs require 1 to 4 unique timeframes");
        }
        strategies.find(accountId, strategyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User strategy was not found: " + strategyId));
        TradingPair pair = new TradingPair(symbol);
        Instant now = clock.instant();
        List<ManualRunChild> children = unique.stream()
                .map(timeframe -> new ManualRunChild(
                        ids.get(), timeframe, ManualRunStatus.PREPARING, null, null))
                .toList();
        return batches.create(new ManualRunBatch(
                ids.get(),
                accountId,
                strategyId,
                pair.symbol(),
                from,
                to,
                executionConfig,
                ManualRunStatus.PREPARING,
                false,
                now,
                now,
                children));
    }

    public void execute(UUID batchId) {
        ManualRunBatch batch = batches.find(batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Manual run batch was not found: " + batchId));
        batches.markRunning(batchId, clock.instant());
        var strategy = strategies.find(batch.accountId(), batch.strategyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "User strategy was not found: " + batch.strategyId()));
        for (ManualRunChild child : batch.children()) {
            if (child.status() != ManualRunStatus.PREPARING) {
                continue;
            }
            ManualRunBatch current = batches.find(batchId).orElseThrow();
            if (current.cancelRequested()) {
                break;
            }
            try {
                var candles = marketData.loadHistorical(
                        new TradingPair(batch.symbol()), child.timeframe(), batch.from(), batch.to());
                var command = new SingleExperimentCommand(
                        batch.symbol(),
                        child.timeframe(),
                        "manual-batch-v1",
                        candles,
                        strategy.document().strategies(),
                        strategy.document().combinationPolicy(),
                        batch.executionConfig(),
                        new GeneratorSnapshot(
                                "manual",
                                "1.0",
                                Map.of("batchId", batch.id().toString(), "childId", child.id().toString()),
                                null),
                        batch.accountId());
                var details = pipeline.execute(plans.create(command));
                batches.completeChild(child.id(), details.experiment().id(), clock.instant());
            } catch (RuntimeException failure) {
                batches.failChild(child.id(), safeMessage(failure), clock.instant());
            }
        }
        batches.finish(batchId, clock.instant());
    }

    public ManualRunBatch get(UUID accountId, UUID batchId) {
        return batches.find(accountId, batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Manual run batch was not found: " + batchId));
    }

    public List<ManualRunBatch> list(UUID accountId) {
        return batches.findAll(accountId);
    }

    public List<UUID> recoverableBatchIds() {
        return batches.findRecoverable().stream().map(ManualRunBatch::id).toList();
    }

    public ManualRunBatch cancel(UUID accountId, UUID batchId) {
        get(accountId, batchId);
        batches.requestCancellation(accountId, batchId, clock.instant());
        return get(accountId, batchId);
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
