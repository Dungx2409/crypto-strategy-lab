package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.SearchProgress;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStartCommand;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditionEvaluator;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.experiment.port.SearchProgressPublisher;
import com.cryptolab.experiment.port.SearchTelemetry;
import com.cryptolab.experiment.port.StrategyGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class SearchCoordinator {

    private final Map<String, StrategyGenerator> generators;
    private final String defaultGeneratorType;
    private final SearchRunRepository repository;
    private final StopConditionEvaluator stopConditions;
    private final Clock clock;
    private final JobDispatchMetadata dispatchMetadata;
    private final SearchProgressPublisher progressPublisher;
    private final SearchTelemetry telemetry;

    public SearchCoordinator(
            StrategyGenerator generator,
            SearchRunRepository repository,
            StopConditionEvaluator stopConditions,
            Clock clock,
            JobDispatchMetadata dispatchMetadata) {
        this(generator, repository, stopConditions, clock, dispatchMetadata,
                SearchProgressPublisher.noop(), SearchTelemetry.noop());
    }

    public SearchCoordinator(
            StrategyGenerator generator,
            SearchRunRepository repository,
            StopConditionEvaluator stopConditions,
            Clock clock,
            JobDispatchMetadata dispatchMetadata,
            SearchProgressPublisher progressPublisher) {
        this(generator, repository, stopConditions, clock, dispatchMetadata,
                progressPublisher, SearchTelemetry.noop());
    }

    public SearchCoordinator(
            StrategyGenerator generator,
            SearchRunRepository repository,
            StopConditionEvaluator stopConditions,
            Clock clock,
            JobDispatchMetadata dispatchMetadata,
            SearchProgressPublisher progressPublisher,
            SearchTelemetry telemetry) {
        this(List.of(generator), generator.type(), repository, stopConditions, clock,
                dispatchMetadata, progressPublisher, telemetry);
    }

    public SearchCoordinator(
            List<StrategyGenerator> generators,
            String defaultGeneratorType,
            SearchRunRepository repository,
            StopConditionEvaluator stopConditions,
            Clock clock,
            JobDispatchMetadata dispatchMetadata,
            SearchProgressPublisher progressPublisher,
            SearchTelemetry telemetry) {
        LinkedHashMap<String, StrategyGenerator> available = new LinkedHashMap<>();
        for (StrategyGenerator generator : List.copyOf(generators)) {
            String key = normalizeGeneratorType(generator.type());
            if (available.putIfAbsent(key, generator) != null) {
                throw new IllegalArgumentException("duplicate strategy generator: " + key);
            }
        }
        if (available.isEmpty()) {
            throw new IllegalArgumentException("at least one strategy generator is required");
        }
        this.defaultGeneratorType = normalizeGeneratorType(defaultGeneratorType);
        if (!available.containsKey(this.defaultGeneratorType)) {
            throw new IllegalArgumentException("unsupported default strategy generator: " + defaultGeneratorType);
        }
        this.generators = Map.copyOf(available);
        this.repository = repository;
        this.stopConditions = stopConditions;
        this.clock = clock;
        this.dispatchMetadata = dispatchMetadata;
        this.progressPublisher = progressPublisher;
        this.telemetry = telemetry;
    }

    public SearchRunSummary start(SearchStartCommand command) {
        create(command);
        return run(command);
    }

    public SearchRunSummary create(SearchStartCommand command) {
        return create(command, defaultGeneratorType);
    }

    public SearchRunSummary create(SearchStartCommand command, String requestedGeneratorType) {
        StrategyGenerator generator = generator(requestedGeneratorType);
        Instant createdAt = clock.instant();
        SearchRun run = new SearchRun(
                command.context().searchRunId(),
                SearchRunStatus.CREATED,
                command.context(),
                generator.type(),
                generator.version(),
                createdAt,
                null,
                null,
                false);
        repository.create(run, command.executionConfig());
        return publish(details(run.id()));
    }

    public List<String> availableGeneratorTypes() {
        return generators.keySet().stream().sorted().toList();
    }

    public String defaultGeneratorType() {
        return defaultGeneratorType;
    }

    public SearchRunSummary run(SearchStartCommand command) {
        UUID searchRunId = command.context().searchRunId();
        SearchRunSummary prepared = details(searchRunId);
        if (prepared.run().status() == SearchRunStatus.CANCELLED) {
            return prepared;
        }
        if (prepared.run().status() != SearchRunStatus.CREATED) {
            throw new IllegalStateException("search run is not ready to start: " + searchRunId);
        }
        if (prepared.run().cancelRequested()) {
            repository.transition(
                    searchRunId,
                    SearchRunStatus.CREATED,
                    SearchRunStatus.CANCELLED,
                    SearchStopReason.USER_CANCELLED,
                    clock.instant());
            return details(searchRunId);
        }
        Instant startedAt = clock.instant();
        repository.transition(
                searchRunId, SearchRunStatus.CREATED, SearchRunStatus.RUNNING, null, startedAt);
        telemetry.runStarted(searchRunId, prepared.run().generatorType());
        publish(details(searchRunId));

        SearchStopReason reason;
        try {
            reason = generateInBoundedBatches(searchRunId, startedAt, command);
        } catch (RuntimeException exception) {
            SearchRunSummary current = details(searchRunId);
            if (current.run().status() == SearchRunStatus.CANCELLED) {
                telemetry.runFinished(searchRunId, SearchRunStatus.CANCELLED);
                return publish(current);
            }
            repository.fail(searchRunId, "SEARCH_GENERATION_FAILED", safeMessage(exception), clock.instant());
            telemetry.runFinished(searchRunId, SearchRunStatus.FAILED);
            throw exception;
        }

        SearchRunSummary current = details(searchRunId);
        if (current.run().status() == SearchRunStatus.CANCELLED) {
            telemetry.runFinished(searchRunId, SearchRunStatus.CANCELLED);
            return publish(current);
        }

        if (reason == SearchStopReason.USER_CANCELLED) {
            repository.transition(
                    searchRunId,
                    SearchRunStatus.RUNNING,
                    SearchRunStatus.CANCELLED,
                    reason,
                    clock.instant());
            telemetry.runFinished(searchRunId, SearchRunStatus.CANCELLED);
            return publish(details(searchRunId));
        }

        repository.finishGeneration(searchRunId, reason, clock.instant());
        SearchRunSummary finishedGeneration = details(searchRunId);
        if (finishedGeneration.run().status() == SearchRunStatus.COMPLETED) {
            telemetry.runFinished(searchRunId, SearchRunStatus.COMPLETED);
        }
        return publish(finishedGeneration);
    }

    public SearchRunSummary cancel(UUID searchRunId) {
        boolean accepted = repository.cancel(searchRunId, clock.instant());
        SearchRunSummary summary = details(searchRunId);
        if (!accepted && !summary.run().status().equals(SearchRunStatus.CANCELLED)) {
            return summary;
        }
        return publish(summary);
    }

    public SearchRunSummary details(UUID searchRunId) {
        return repository.findSummary(searchRunId)
                .orElseThrow(() -> new SearchRunNotFoundException(searchRunId));
    }

    public void recordEvaluation(UUID searchRunId, BigDecimal score) {
        repository.recordEvaluation(searchRunId, score);
    }

    private SearchStopReason generateInBoundedBatches(
            UUID searchRunId,
            Instant startedAt,
            SearchStartCommand command) {
        long generated = 0;
        long persisted = 0;
        StrategyGenerator generator = generator(details(searchRunId).run().generatorType());
        int generationSize = Math.max(1, generator.generationSize(command.context()));
        int generatedInGeneration = 0;
        try (Stream<CandidateStrategy> stream = generator.generate(
                command.context(), repository::awaitCandidateFitness)) {
            Iterator<CandidateStrategy> candidates = stream.iterator();
            while (true) {
                SearchRunSummary stored = details(searchRunId);
                if (stored.run().cancelRequested()) {
                    return SearchStopReason.USER_CANCELLED;
                }
                var stop = stopConditions.evaluate(
                        command.context().stopConditions(),
                        progress(startedAt, generated, persisted, stored));
                if (stop.isPresent()) {
                    return stop.orElseThrow();
                }

                int generationRemaining = generationSize - generatedInGeneration;
                int batchLimit = Math.max(1, Math.min(
                        command.context().batchSize(), generationRemaining));
                List<CandidateStrategy> batch = new ArrayList<>(batchLimit);
                SearchStopReason afterBatch = null;
                while (batch.size() < batchLimit) {
                    stop = stopConditions.evaluate(
                            command.context().stopConditions(),
                            progress(startedAt, generated, persisted, stored));
                    if (stop.isPresent()) {
                        afterBatch = stop.orElseThrow();
                        break;
                    }
                    if (!candidates.hasNext()) {
                        afterBatch = SearchStopReason.SOURCE_EXHAUSTED;
                        break;
                    }
                    batch.add(candidates.next());
                    generated++;
                    generatedInGeneration++;
                    telemetry.candidatesGenerated(searchRunId, 1);
                }
                if (!batch.isEmpty()) {
                    persisted += repository.appendCandidatesAndCreateJobs(
                            stored.run(),
                            command.executionConfig(),
                            dispatchMetadata,
                            batch,
                            clock.instant());
                    publish(details(searchRunId));
                }
                if (generatedInGeneration == generationSize) {
                    generatedInGeneration = 0;
                }
                if (details(searchRunId).run().cancelRequested()) {
                    return SearchStopReason.USER_CANCELLED;
                }
                if (afterBatch != null) {
                    return afterBatch;
                }
            }
        }
    }

    private SearchProgress progress(
            Instant startedAt,
            long generated,
            long persisted,
            SearchRunSummary stored) {
        return new SearchProgress(
                startedAt,
                clock.instant(),
                generated,
                persisted,
                stored.bestScore(),
                stored.noImprovementIterations());
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private SearchRunSummary publish(SearchRunSummary summary) {
        try {
            progressPublisher.publish(summary);
        } catch (RuntimeException ignoredPresentationFailure) {
            // Search durability must not depend on an ephemeral WebSocket subscriber.
        }
        return summary;
    }

    private StrategyGenerator generator(String type) {
        String normalized = normalizeGeneratorType(type == null ? defaultGeneratorType : type);
        StrategyGenerator generator = generators.get(normalized);
        if (generator == null) {
            throw new IllegalArgumentException(
                    "unsupported strategy generator: " + type + "; available=" + availableGeneratorTypes());
        }
        return generator;
    }

    private static String normalizeGeneratorType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("generator type must not be blank");
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
