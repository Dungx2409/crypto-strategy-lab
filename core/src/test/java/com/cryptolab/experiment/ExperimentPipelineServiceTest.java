package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.EquityPoint;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.Experiment;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.SortDirection;
import com.cryptolab.experiment.port.ExperimentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentPipelineServiceTest {

    private static final UUID SEARCH_RUN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void executesCandidateBacktestEvaluateAndRankInThatOrder() {
        List<String> operations = new ArrayList<>();
        RecordingRepository repository = new RecordingRepository(operations);
        BacktestResult backtestResult = new BacktestResult(
                ExperimentTestFixtures.EXPERIMENT_ID,
                ExperimentTestFixtures.CANDIDATE_ID,
                List.of(),
                List.of(),
                List.of(new EquityPoint(NOW, new BigDecimal("10100"))),
                new BigDecimal("10100"),
                NOW,
                NOW,
                "test-engine-v1");
        DefaultExperimentEvaluator delegateEvaluator = new DefaultExperimentEvaluator();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ExperimentPipelineService pipeline = new ExperimentPipelineService(
                command -> {
                    operations.add("backtest");
                    return backtestResult;
                },
                new com.cryptolab.experiment.port.ExperimentEvaluator() {
                    @Override
                    public String version() {
                        return delegateEvaluator.version();
                    }

                    @Override
                    public Evaluation evaluate(
                            BacktestResult result,
                            com.cryptolab.experiment.domain.ExecutionConfig config,
                            Instant evaluatedAt) {
                        operations.add("evaluate");
                        return delegateEvaluator.evaluate(result, config, evaluatedAt);
                    }
                },
                new DefaultRankingService(),
                repository,
                clock,
                UUID::randomUUID);
        ExperimentPlan plan = new ExperimentPlan(
                ExperimentTestFixtures.EXPERIMENT_ID,
                SEARCH_RUN_ID,
                ExperimentTestFixtures.candidate(),
                ExperimentTestFixtures.dataset(),
                ExperimentTestFixtures.executionConfig(),
                new GeneratorSnapshot("manual", "1.0", Map.of("maxCandidates", 1), null),
                delegateEvaluator.version(),
                "test-commit",
                "test-build",
                null,
                NOW);

        ExperimentDetails details = pipeline.execute(plan);

        assertThat(operations).containsExactly("candidate", "backtest", "evaluate", "rank", "project");
        assertThat(details.experiment().status()).isEqualTo(ExperimentStatus.COMPLETED);
        assertThat(details.rank()).isEqualTo(1);
    }

    private static final class RecordingRepository implements ExperimentRepository {

        private final List<String> operations;
        private ExperimentPlan plan;
        private BacktestResult result;
        private Evaluation evaluation;
        private List<Ranking> rankings = List.of();

        private RecordingRepository(List<String> operations) {
            this.operations = operations;
        }

        @Override
        public void create(ExperimentPlan plan) {
            operations.add("candidate");
            this.plan = plan;
        }

        @Override
        public void transition(UUID experimentId, ExperimentStatus expected, ExperimentStatus target, Instant at) {}

        @Override
        public void complete(
                UUID experimentId,
                BacktestResult result,
                Evaluation evaluation,
                Instant completedAt) {
            this.result = result;
            this.evaluation = evaluation;
        }

        @Override
        public void fail(UUID experimentId, String failureCode, String failureMessage, Instant failedAt) {}

        @Override
        public List<Evaluation> findCompletedEvaluations(UUID searchRunId) {
            operations.add("rank");
            return List.of(evaluation);
        }

        @Override
        public void replaceLeaderboard(UUID searchRunId, List<Ranking> rankings, Instant updatedAt) {
            operations.add("project");
            this.rankings = List.copyOf(rankings);
        }

        @Override
        public List<LeaderboardEntry> findLeaderboard(
                UUID searchRunId, int limit, LeaderboardSort sort, SortDirection direction) {
            return List.of();
        }

        @Override
        public Optional<ExperimentDetails> findDetails(UUID experimentId) {
            if (result == null || evaluation == null) {
                return Optional.empty();
            }
            Experiment experiment = new Experiment(
                    plan.experimentId(),
                    plan.candidate().candidateId(),
                    plan.searchRunId(),
                    ExperimentStatus.COMPLETED,
                    plan.dataset().reference(),
                    plan.executionConfig(),
                    plan.candidate().strategies(),
                    plan.candidate().combinationPolicy(),
                    plan.generator().type(),
                    plan.generator().version(),
                    plan.generator().randomSeed(),
                    plan.evaluatorVersion(),
                    plan.codeCommit(),
                    plan.buildVersion(),
                    plan.reproductionOfExperimentId(),
                    NOW,
                    NOW,
                    null,
                    null,
                    2);
            return Optional.of(new ExperimentDetails(
                    experiment,
                    plan.candidate(),
                    plan.generator(),
                    result.signals(),
                    result.trades(),
                    evaluation.metrics(),
                    rankings.getFirst().rank()));
        }

        @Override
        public Optional<ExperimentPlan> findPlan(UUID experimentId) {
            return Optional.ofNullable(plan);
        }
    }
}
