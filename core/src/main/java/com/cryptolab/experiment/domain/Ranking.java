package com.cryptolab.experiment.domain;

import java.util.Objects;
import java.util.UUID;

public record Ranking(int rank, UUID experimentId, EvaluationMetrics metrics) {

    public Ranking {
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive");
        }
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }
}
