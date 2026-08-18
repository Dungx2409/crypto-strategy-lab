package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestJobClaim;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface BacktestWorkerRepository {

    BacktestJobClaim claim(UUID experimentId, String workerId, Duration lease, Instant claimedAt);

    void complete(
            BacktestJobClaim claim,
            BacktestResult result,
            Evaluation evaluation,
            DomainEventEnvelope<BacktestCompletedEvent> completedEvent,
            Instant completedAt);

    boolean scheduleRetry(
            BacktestJobClaim claim,
            BacktestJob retryJob,
            String failureMessage,
            Instant retryAt,
            Instant failedAt);

    void failPermanently(
            BacktestJobClaim claim,
            String failureCode,
            String failureMessage,
            Instant failedAt);
}
