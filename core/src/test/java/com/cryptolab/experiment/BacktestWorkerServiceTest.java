package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.BacktestWorkerService;
import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestJobClaim;
import com.cryptolab.experiment.domain.BacktestJobClaimDecision;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import com.cryptolab.experiment.domain.EquityPoint;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.port.BacktestPort;
import com.cryptolab.experiment.port.BacktestWorkerRepository;
import com.cryptolab.experiment.port.ExperimentEvaluator;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BacktestWorkerServiceTest {

    private static final UUID EXPERIMENT_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID SEARCH_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-18T14:00:00Z");

    @Test
    void completesOnceAndAcknowledgesDuplicateWithoutRunningBacktestAgain() {
        RecordingRepository repository = new RecordingRepository();
        AtomicInteger executions = new AtomicInteger();
        BacktestWorkerService worker = worker(command -> {
            executions.incrementAndGet();
            return result();
        }, repository);

        assertThat(worker.process(EXPERIMENT_ID, "worker-1"))
                .isEqualTo(BacktestWorkerOutcome.COMPLETED);
        assertThat(worker.process(EXPERIMENT_ID, "worker-2"))
                .isEqualTo(BacktestWorkerOutcome.DUPLICATE_ACKNOWLEDGED);

        assertThat(executions).hasValue(1);
        assertThat(repository.completedEvents).isEqualTo(1);
        assertThat(repository.completedEvent.eventType()).isEqualTo("BacktestCompleted");
        assertThat(repository.completedEvent.payload().experimentId()).isEqualTo(EXPERIMENT_ID);
    }

    @Test
    void schedulesAtMostThreeRetriesThenFailsPermanently() {
        RecordingRepository repository = new RecordingRepository();
        BacktestWorkerService worker = worker(command -> {
            throw new IllegalStateException("temporary database dependency");
        }, repository);

        assertThat(worker.process(EXPERIMENT_ID, "worker-1"))
                .isEqualTo(BacktestWorkerOutcome.RETRY_SCHEDULED);
        assertThat(worker.process(EXPERIMENT_ID, "worker-1"))
                .isEqualTo(BacktestWorkerOutcome.RETRY_SCHEDULED);
        assertThat(worker.process(EXPERIMENT_ID, "worker-1"))
                .isEqualTo(BacktestWorkerOutcome.RETRY_SCHEDULED);
        assertThat(worker.process(EXPERIMENT_ID, "worker-1"))
                .isEqualTo(BacktestWorkerOutcome.DEAD_LETTER);

        assertThat(repository.retryCount).isEqualTo(3);
        assertThat(repository.permanentFailures).isEqualTo(1);
        assertThat(repository.lastFailureCode).isEqualTo("RETRIES_EXHAUSTED");
    }

    @Test
    void invalidConfigurationIsPoisonAndIsNotRetried() {
        RecordingRepository repository = new RecordingRepository();
        BacktestWorkerService worker = worker(command -> {
            throw new IllegalArgumentException("invalid candidate");
        }, repository);

        assertThat(worker.process(EXPERIMENT_ID, "worker-1"))
                .isEqualTo(BacktestWorkerOutcome.DEAD_LETTER);

        assertThat(repository.retryCount).isZero();
        assertThat(repository.permanentFailures).isEqualTo(1);
        assertThat(repository.lastFailureCode).isEqualTo("INVALID_JOB");
    }

    @Test
    void requeuesAnActiveClaimSoAWorkerCrashCannotLoseTheOnlyDelivery() {
        RecordingRepository repository = new RecordingRepository();
        repository.claimDecision = BacktestJobClaimDecision.IN_PROGRESS;
        AtomicInteger executions = new AtomicInteger();
        BacktestWorkerService worker = worker(command -> {
            executions.incrementAndGet();
            return result();
        }, repository);

        assertThat(worker.process(EXPERIMENT_ID, "worker-2"))
                .isEqualTo(BacktestWorkerOutcome.REQUEUE);
        assertThat(executions).hasValue(0);
    }

    @Test
    void acknowledgesWithoutRetryWhenSearchWasCancelledDuringAFailedExecution() {
        RecordingRepository repository = new RecordingRepository();
        repository.retryScheduled = false;
        BacktestWorkerService worker = worker(command -> {
            throw new IllegalStateException("temporary failure after cancellation");
        }, repository);

        assertThat(worker.process(EXPERIMENT_ID, "worker-1"))
                .isEqualTo(BacktestWorkerOutcome.DUPLICATE_ACKNOWLEDGED);
        assertThat(repository.retryCount).isZero();
    }

    private static BacktestWorkerService worker(
            BacktestPort backtest,
            RecordingRepository repository) {
        ExperimentEvaluator evaluator = new ExperimentEvaluator() {
            @Override
            public String version() {
                return "test-evaluator-v1";
            }

            @Override
            public Evaluation evaluate(
                    BacktestResult result,
                    com.cryptolab.experiment.domain.ExecutionConfig executionConfig,
                    Instant evaluatedAt) {
                return new Evaluation(
                        result.experimentId(),
                        new EvaluationMetrics(BigDecimal.ONE, BigDecimal.ZERO, 0, BigDecimal.ONE),
                        version(),
                        evaluatedAt);
            }
        };
        return new BacktestWorkerService(
                backtest,
                evaluator,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5));
    }

    private static BacktestResult result() {
        return new BacktestResult(
                EXPERIMENT_ID,
                ExperimentTestFixtures.candidate().candidateId(),
                List.of(),
                List.of(),
                List.of(new EquityPoint(NOW, new BigDecimal("10000"))),
                new BigDecimal("10000"),
                NOW,
                NOW,
                "test-engine-v1");
    }

    private static final class RecordingRepository implements BacktestWorkerRepository {

        private int retryCount;
        private int permanentFailures;
        private int completedEvents;
        private String lastFailureCode;
        private boolean completed;
        private boolean retryScheduled = true;
        private BacktestJobClaimDecision claimDecision;
        private DomainEventEnvelope<BacktestCompletedEvent> completedEvent;

        @Override
        public BacktestJobClaim claim(
                UUID experimentId,
                String workerId,
                Duration lease,
                Instant claimedAt) {
            if (claimDecision != null) {
                return BacktestJobClaim.terminal(claimDecision, experimentId, retryCount);
            }
            if (completed) {
                return BacktestJobClaim.terminal(
                        BacktestJobClaimDecision.COMPLETED, experimentId, retryCount);
            }
            return new BacktestJobClaim(
                    BacktestJobClaimDecision.CLAIMED,
                    experimentId,
                    SEARCH_ID,
                    new BacktestJob(
                            new com.cryptolab.experiment.domain.BacktestCommand(
                                    experimentId,
                                    ExperimentTestFixtures.candidate().candidateId(),
                                    ExperimentTestFixtures.dataset().reference(),
                                    ExperimentTestFixtures.executionConfig()),
                            retryCount,
                            SEARCH_ID.toString()),
                    retryCount,
                    workerId);
        }

        @Override
        public void complete(
                BacktestJobClaim claim,
                BacktestResult result,
                Evaluation evaluation,
                DomainEventEnvelope<BacktestCompletedEvent> completedEvent,
                Instant completedAt) {
            completed = true;
            completedEvents++;
            this.completedEvent = completedEvent;
        }

        @Override
        public boolean scheduleRetry(
                BacktestJobClaim claim,
                BacktestJob retryJob,
                String failureMessage,
                Instant retryAt,
                Instant failedAt) {
            if (retryScheduled) {
                retryCount = retryJob.attempt();
            }
            return retryScheduled;
        }

        @Override
        public void failPermanently(
                BacktestJobClaim claim,
                String failureCode,
                String failureMessage,
                Instant failedAt) {
            permanentFailures++;
            lastFailureCode = failureCode;
        }
    }
}
