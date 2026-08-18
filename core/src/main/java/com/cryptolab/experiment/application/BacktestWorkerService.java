package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestJobClaim;
import com.cryptolab.experiment.domain.BacktestJobClaimDecision;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.port.BacktestPort;
import com.cryptolab.experiment.port.BacktestJobProcessor;
import com.cryptolab.experiment.port.BacktestWorkerRepository;
import com.cryptolab.experiment.port.ExperimentEvaluator;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class BacktestWorkerService implements BacktestJobProcessor {

    public static final int MAX_RETRIES = 3;
    private static final String COMPLETED_EVENT_TYPE = "BacktestCompleted";

    private final BacktestPort backtest;
    private final ExperimentEvaluator evaluator;
    private final BacktestWorkerRepository repository;
    private final Clock clock;
    private final Duration claimLease;

    public BacktestWorkerService(
            BacktestPort backtest,
            ExperimentEvaluator evaluator,
            BacktestWorkerRepository repository,
            Clock clock,
            Duration claimLease) {
        this.backtest = Objects.requireNonNull(backtest, "backtest must not be null");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (claimLease == null || claimLease.isZero() || claimLease.isNegative()) {
            throw new IllegalArgumentException("claimLease must be positive");
        }
        this.claimLease = claimLease;
    }

    @Override
    public BacktestWorkerOutcome process(UUID experimentId, String workerId) {
        BacktestJobClaim claim = repository.claim(experimentId, workerId, claimLease, clock.instant());
        return switch (claim.decision()) {
            case AWAITING_DISPATCH_CONFIRMATION, IN_PROGRESS -> BacktestWorkerOutcome.REQUEUE;
            case COMPLETED, CANCELLED -> BacktestWorkerOutcome.DUPLICATE_ACKNOWLEDGED;
            case FAILED, NOT_FOUND -> BacktestWorkerOutcome.DEAD_LETTER;
            case CLAIMED -> executeClaimed(claim);
        };
    }

    private BacktestWorkerOutcome executeClaimed(BacktestJobClaim claim) {
        try {
            BacktestResult result = backtest.run(claim.job().command());
            Evaluation evaluation = evaluator.evaluate(
                    result, claim.job().command().executionConfig(), clock.instant());
            Instant completedAt = clock.instant();
            repository.complete(
                    claim,
                    result,
                    evaluation,
                    completedEvent(claim, evaluation, completedAt),
                    completedAt);
            return BacktestWorkerOutcome.COMPLETED;
        } catch (IllegalArgumentException poison) {
            repository.failPermanently(
                    claim, "INVALID_JOB", safeMessage(poison), clock.instant());
            return BacktestWorkerOutcome.DEAD_LETTER;
        } catch (RuntimeException transientFailure) {
            if (claim.retryCount() < MAX_RETRIES) {
                int nextRetry = claim.retryCount() + 1;
                Instant failedAt = clock.instant();
                boolean scheduled = repository.scheduleRetry(
                        claim,
                        new BacktestJob(
                                claim.job().command(), nextRetry, claim.job().correlationId()),
                        safeMessage(transientFailure),
                        failedAt.plus(retryDelay(nextRetry)),
                        failedAt);
                return scheduled
                        ? BacktestWorkerOutcome.RETRY_SCHEDULED
                        : BacktestWorkerOutcome.DUPLICATE_ACKNOWLEDGED;
            }
            repository.failPermanently(
                    claim, "RETRIES_EXHAUSTED", safeMessage(transientFailure), clock.instant());
            return BacktestWorkerOutcome.DEAD_LETTER;
        }
    }

    private DomainEventEnvelope<BacktestCompletedEvent> completedEvent(
            BacktestJobClaim claim,
            Evaluation evaluation,
            Instant completedAt) {
        UUID eventId = UUID.nameUUIDFromBytes(
                (COMPLETED_EVENT_TYPE + ":" + claim.experimentId())
                        .getBytes(StandardCharsets.UTF_8));
        BacktestCompletedEvent payload = new BacktestCompletedEvent(
                claim.experimentId(),
                claim.searchRunId(),
                evaluation.metrics(),
                evaluation.evaluatorVersion(),
                completedAt);
        return new DomainEventEnvelope<>(
                eventId,
                COMPLETED_EVENT_TYPE,
                1,
                completedAt,
                "Experiment",
                claim.experimentId(),
                claim.job().correlationId(),
                claim.job().command().experimentId().toString(),
                claim.experimentId().toString(),
                payload);
    }

    private static Duration retryDelay(int retryNumber) {
        return Duration.ofSeconds(1L << Math.min(retryNumber - 1, 6));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
