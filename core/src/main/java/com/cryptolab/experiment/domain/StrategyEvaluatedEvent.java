package com.cryptolab.experiment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StrategyEvaluatedEvent(
        UUID experimentId,
        UUID searchRunId,
        EvaluationMetrics metrics,
        String evaluatorVersion,
        Instant evaluatedAt) {

    public StrategyEvaluatedEvent {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        if (evaluatorVersion == null || evaluatorVersion.isBlank()) {
            throw new IllegalArgumentException("evaluatorVersion must not be blank");
        }
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    }
}
