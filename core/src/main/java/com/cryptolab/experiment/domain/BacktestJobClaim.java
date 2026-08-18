package com.cryptolab.experiment.domain;

import java.util.Objects;
import java.util.UUID;

public record BacktestJobClaim(
        BacktestJobClaimDecision decision,
        UUID experimentId,
        UUID searchRunId,
        BacktestJob job,
        int retryCount,
        String workerId) {

    public BacktestJobClaim {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (decision == BacktestJobClaimDecision.CLAIMED) {
            Objects.requireNonNull(searchRunId, "searchRunId is required for a claimed job");
            Objects.requireNonNull(job, "job is required for a claimed job");
            if (workerId == null || workerId.isBlank()) {
                throw new IllegalArgumentException("workerId is required for a claimed job");
            }
            if (!experimentId.equals(job.command().experimentId())) {
                throw new IllegalArgumentException("claimed job experiment identity mismatch");
            }
        }
    }

    public static BacktestJobClaim terminal(
            BacktestJobClaimDecision decision,
            UUID experimentId,
            int retryCount) {
        if (decision == BacktestJobClaimDecision.CLAIMED) {
            throw new IllegalArgumentException("use the canonical constructor for a claimed job");
        }
        return new BacktestJobClaim(decision, experimentId, null, null, retryCount, null);
    }
}
