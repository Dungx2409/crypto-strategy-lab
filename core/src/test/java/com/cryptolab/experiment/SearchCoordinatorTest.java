package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStartCommand;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditionEvaluator;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.experiment.port.StrategyGenerator;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SearchCoordinatorTest {

    private static final UUID SEARCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void stopsAtMaximumAndOnlyRequestsBoundedBatchesFromAnInfiniteStream() {
        AtomicInteger requested = new AtomicInteger();
        RecordingRepository repository = new RecordingRepository();
        SearchCoordinator coordinator = coordinator(generator(requested), repository);

        SearchRunSummary result = coordinator.start(command(new StopConditions(7L, null, null), 3));

        assertThat(result.run().status()).isEqualTo(SearchRunStatus.EVALUATING);
        assertThat(result.stopReason()).isEqualTo(SearchStopReason.MAX_CANDIDATES);
        assertThat(result.generatedCandidates()).isEqualTo(7);
        assertThat(result.persistedCandidates()).isEqualTo(7);
        assertThat(repository.batchSizes).containsExactly(3, 3, 1);
        assertThat(requested).hasValue(7);
    }

    @Test
    void observesCancellationAtTheNextBatchBoundary() {
        AtomicInteger requested = new AtomicInteger();
        RecordingRepository repository = new RecordingRepository();
        repository.cancelAfterFirstBatch = true;
        SearchCoordinator coordinator = coordinator(generator(requested), repository);

        SearchRunSummary result = coordinator.start(command(new StopConditions(100L, null, null), 4));

        assertThat(result.run().status()).isEqualTo(SearchRunStatus.CANCELLED);
        assertThat(result.stopReason()).isEqualTo(SearchStopReason.USER_CANCELLED);
        assertThat(result.generatedCandidates()).isEqualTo(4);
        assertThat(repository.batchSizes).containsExactly(4);
    }

    @Test
    void noImprovementFeedbackStopsGenerationWithoutCouplingToEvaluator() {
        RecordingRepository repository = new RecordingRepository();
        repository.noImprovementAfterFirstBatch = 2;
        SearchCoordinator coordinator = coordinator(generator(new AtomicInteger()), repository);

        SearchRunSummary result = coordinator.start(command(new StopConditions(null, null, 2), 5));

        assertThat(result.stopReason()).isEqualTo(SearchStopReason.NO_IMPROVEMENT);
        assertThat(result.generatedCandidates()).isEqualTo(5);
    }

    @Test
    void publishesCreatedRunningBatchAndEvaluatingProgressWithoutCouplingToWebsocket() {
        RecordingRepository repository = new RecordingRepository();
        List<SearchRunSummary> updates = new ArrayList<>();
        SearchCoordinator coordinator = new SearchCoordinator(
                generator(new AtomicInteger()),
                repository,
                new StopConditionEvaluator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new JobDispatchMetadata("evaluator-v1", "test-commit", "test-build"),
                updates::add);

        coordinator.start(command(new StopConditions(3L, null, null), 2));

        assertThat(updates).extracting(update -> update.run().status())
                .containsSequence(SearchRunStatus.CREATED, SearchRunStatus.RUNNING)
                .endsWith(SearchRunStatus.EVALUATING);
        assertThat(updates).extracting(SearchRunSummary::generatedCandidates)
                .contains(2L, 3L);
    }

    @Test
    void selectsARequestedGeneratorWithoutChangingSearchOrBacktestContracts() {
        RecordingRepository repository = new RecordingRepository();
        StrategyGenerator random = namedGenerator("random", 10);
        StrategyGenerator genetic = namedGenerator("genetic", 20);
        SearchCoordinator coordinator = new SearchCoordinator(
                List.of(random, genetic),
                "random",
                repository,
                new StopConditionEvaluator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new JobDispatchMetadata("evaluator-v1", "test-commit", "test-build"),
                summary -> {},
                com.cryptolab.experiment.port.SearchTelemetry.noop());

        coordinator.create(command(new StopConditions(1L, null, null), 1), "genetic");
        SearchRunSummary result = coordinator.run(command(new StopConditions(1L, null, null), 1));

        assertThat(result.run().generatorType()).isEqualTo("genetic");
        assertThat(repository.batchSizes).containsExactly(1);
        assertThat(coordinator.availableGeneratorTypes()).containsExactly("genetic", "random");
    }

    private static SearchCoordinator coordinator(
            StrategyGenerator generator,
            RecordingRepository repository) {
        return new SearchCoordinator(
                generator,
                repository,
                new StopConditionEvaluator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new JobDispatchMetadata("evaluator-v1", "test-commit", "test-build"));
    }

    private static SearchStartCommand command(StopConditions stopConditions, int batchSize) {
        SearchContext context = new SearchContext(
                SEARCH_ID,
                ExperimentTestFixtures.dataset().reference(),
                List.of("TEST"),
                Map.of("TEST", "1.0"),
                new SearchParameterSpace(Map.of("TEST", Map.of("value", List.of(1, 2)))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                99,
                stopConditions,
                batchSize);
        return new SearchStartCommand(context, ExperimentTestFixtures.executionConfig());
    }

    private static StrategyGenerator generator(AtomicInteger requested) {
        return new StrategyGenerator() {
            @Override
            public String type() {
                return "test-random";
            }

            @Override
            public String version() {
                return "1.0";
            }

            @Override
            public Stream<CandidateStrategy> generate(SearchContext context) {
                return Stream.generate(() -> candidate(requested.getAndIncrement()));
            }
        };
    }

    private static StrategyGenerator namedGenerator(String type, int candidateOffset) {
        return new StrategyGenerator() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public String version() {
                return "1.0";
            }

            @Override
            public Stream<CandidateStrategy> generate(SearchContext context) {
                return Stream.of(candidate(candidateOffset));
            }
        };
    }

    private static CandidateStrategy candidate(int index) {
        List<StrategyDefinition> strategies = List.of(
                new StrategyDefinition("TEST", "1.0", Map.of("value", index)));
        var policy = new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO);
        return new CandidateStrategy(
                new UUID(0, index + 1L), strategies, policy, CandidateCanonicalizer.hash(strategies, policy));
    }

    private static final class RecordingRepository implements SearchRunRepository {

        private final List<Integer> batchSizes = new ArrayList<>();
        private SearchRun run;
        private long generated;
        private long persisted;
        private int noImprovement;
        private SearchStopReason stopReason;
        private boolean cancelAfterFirstBatch;
        private int noImprovementAfterFirstBatch;

        @Override
        public void create(SearchRun run, com.cryptolab.experiment.domain.ExecutionConfig executionConfig) {
            this.run = run;
        }

        @Override
        public void transition(
                UUID searchRunId,
                SearchRunStatus expected,
                SearchRunStatus target,
                SearchStopReason stopReason,
                Instant at) {
            this.stopReason = stopReason;
            run = new SearchRun(
                    run.id(),
                    target,
                    run.context(),
                    run.generatorType(),
                    run.generatorVersion(),
                    run.createdAt(),
                    target == SearchRunStatus.RUNNING ? at : run.startedAt(),
                    target == SearchRunStatus.COMPLETED || target == SearchRunStatus.CANCELLED
                            || target == SearchRunStatus.FAILED ? at : null,
                    run.cancelRequested());
        }

        @Override
        public void finishGeneration(
                UUID searchRunId, SearchStopReason stopReason, Instant at) {
            transition(
                    searchRunId,
                    SearchRunStatus.RUNNING,
                    SearchRunStatus.EVALUATING,
                    stopReason,
                    at);
        }

        @Override
        public int appendCandidatesAndCreateJobs(
                SearchRun run,
                ExecutionConfig executionConfig,
                JobDispatchMetadata dispatchMetadata,
                List<CandidateStrategy> candidates,
                Instant generatedAt) {
            batchSizes.add(candidates.size());
            generated += candidates.size();
            persisted += candidates.size();
            if (cancelAfterFirstBatch && batchSizes.size() == 1) {
                this.run = copyWithCancellation(this.run);
            }
            if (noImprovementAfterFirstBatch > 0 && batchSizes.size() == 1) {
                noImprovement = noImprovementAfterFirstBatch;
            }
            return candidates.size();
        }

        @Override
        public boolean cancel(UUID searchRunId, Instant cancelledAt) {
            run = new SearchRun(
                    run.id(), SearchRunStatus.CANCELLED, run.context(), run.generatorType(),
                    run.generatorVersion(), run.createdAt(), run.startedAt(), cancelledAt, true);
            stopReason = SearchStopReason.USER_CANCELLED;
            return true;
        }

        @Override
        public void recordEvaluation(UUID searchRunId, BigDecimal score) {}

        @Override
        public void fail(UUID searchRunId, String failureCode, String failureMessage, Instant failedAt) {
            transition(searchRunId, run.status(), SearchRunStatus.FAILED, SearchStopReason.FAILED, failedAt);
        }

        @Override
        public Optional<SearchRunSummary> findSummary(UUID searchRunId) {
            return Optional.of(new SearchRunSummary(
                    run, generated, persisted, persisted, 0, 0, 0, 0, 0,
                    null, noImprovement, stopReason, null, null));
        }

        private static SearchRun copyWithCancellation(SearchRun source) {
            return new SearchRun(
                    source.id(),
                    source.status(),
                    source.context(),
                    source.generatorType(),
                    source.generatorVersion(),
                    source.createdAt(),
                    source.startedAt(),
                    source.endedAt(),
                    true);
        }
    }
}
