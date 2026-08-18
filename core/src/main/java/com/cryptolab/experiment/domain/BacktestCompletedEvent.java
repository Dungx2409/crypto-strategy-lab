package com.cryptolab.experiment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BacktestCompletedEvent(
        UUID experimentId,
        UUID searchRunId,
        EvaluationMetrics metrics,
        String evaluatorVersion,
        Instant completedAt) {

    public BacktestCompletedEvent {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        if (evaluatorVersion == null || evaluatorVersion.isBlank()) {
            throw new IllegalArgumentException("evaluatorVersion must not be blank");
        }
        Objects.requireNonNull(completedAt, "completedAt must not be null");
    }
}
