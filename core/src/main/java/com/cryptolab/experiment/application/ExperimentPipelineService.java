package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ExperimentProvenance;
import com.cryptolab.experiment.domain.ExperimentStateMachine;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.RerunResult;
import com.cryptolab.experiment.domain.SortDirection;
import com.cryptolab.experiment.port.BacktestPort;
import com.cryptolab.experiment.port.ExperimentEvaluator;
import com.cryptolab.experiment.port.ExperimentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class ExperimentPipelineService {

    private static final BigDecimal REPRODUCTION_TOLERANCE = new BigDecimal("0.00000001");

    private final BacktestPort backtest;
    private final ExperimentEvaluator evaluator;
    private final DefaultRankingService rankingService;
    private final ExperimentRepository repository;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public ExperimentPipelineService(
            BacktestPort backtest,
            ExperimentEvaluator evaluator,
            DefaultRankingService rankingService,
            ExperimentRepository repository,
            Clock clock,
            Supplier<UUID> idGenerator) {
        this.backtest = backtest;
        this.evaluator = evaluator;
        this.rankingService = rankingService;
        this.repository = repository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public ExperimentDetails execute(ExperimentPlan plan) {
        CandidateCanonicalizer.verify(plan.candidate());
        if (!evaluator.version().equals(plan.evaluatorVersion())) {
            throw new IllegalArgumentException("plan evaluator version does not match the configured evaluator");
        }
        repository.create(plan);
        ExperimentStateMachine.requireTransition(ExperimentStatus.CREATED, ExperimentStatus.RUNNING);
        repository.transition(plan.experimentId(), ExperimentStatus.CREATED, ExperimentStatus.RUNNING, clock.instant());
        try {
            BacktestResult result = backtest.run(new BacktestCommand(
                    plan.experimentId(),
                    plan.candidate().candidateId(),
                    plan.dataset().reference(),
                    plan.executionConfig()));
            Evaluation evaluation = evaluator.evaluate(result, plan.executionConfig(), clock.instant());
            ExperimentStateMachine.requireTransition(ExperimentStatus.RUNNING, ExperimentStatus.COMPLETED);
            repository.complete(plan.experimentId(), result, evaluation, clock.instant());
        } catch (RuntimeException exception) {
            repository.fail(
                    plan.experimentId(),
                    "PIPELINE_FAILED",
                    safeMessage(exception),
                    clock.instant());
            throw exception;
        }
        List<Ranking> rankings = rankingService.rank(repository.findCompletedEvaluations(plan.searchRunId()));
        repository.replaceLeaderboard(plan.searchRunId(), rankings, clock.instant());
        return details(plan.experimentId());
    }

    public ExperimentDetails details(UUID experimentId) {
        return repository.findDetails(experimentId)
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId));
    }

    public ExperimentProvenance provenance(UUID experimentId) {
        return ExperimentProvenance.from(details(experimentId));
    }

    public MarketDataset dataset(UUID experimentId) {
        return repository.findPlan(experimentId)
                .map(ExperimentPlan::dataset)
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId));
    }

    public List<LeaderboardEntry> leaderboard(UUID searchRunId, int limit) {
        return leaderboard(searchRunId, limit, LeaderboardSort.SCORE, SortDirection.DESC);
    }

    public List<LeaderboardEntry> leaderboard(
            UUID searchRunId, int limit, LeaderboardSort sort, SortDirection direction) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("leaderboard limit must be between 1 and 500");
        }
        return repository.findLeaderboard(searchRunId, limit, sort, direction);
    }

    public RerunResult rerun(UUID sourceExperimentId) {
        ExperimentPlan source = repository.findPlan(sourceExperimentId)
                .orElseThrow(() -> new ExperimentNotFoundException(sourceExperimentId));
        ExperimentDetails sourceDetails = details(sourceExperimentId);
        ExperimentDetails reproduced = execute(source.reproduceAs(idGenerator.get(), clock.instant()));
        return new RerunResult(
                sourceExperimentId,
                reproduced,
                metricsMatch(sourceDetails.metrics(), reproduced.metrics()));
    }

    private static boolean metricsMatch(EvaluationMetrics left, EvaluationMetrics right) {
        if (left == null || right == null || left.totalTrades() != right.totalTrades()) {
            return false;
        }
        return close(left.totalReturnPct(), right.totalReturnPct())
                && close(left.maxDrawdownPct(), right.maxDrawdownPct())
                && close(left.winRatePct(), right.winRatePct())
                && close(left.score(), right.score());
    }

    private static boolean close(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(REPRODUCTION_TOLERANCE) <= 0;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
