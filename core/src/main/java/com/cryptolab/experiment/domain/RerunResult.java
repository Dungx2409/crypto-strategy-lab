package com.cryptolab.experiment.domain;

import java.util.Objects;
import java.util.UUID;

public record RerunResult(
        UUID sourceExperimentId,
        ExperimentDetails reproducedExperiment,
        boolean metricsMatch) {

    public RerunResult {
        Objects.requireNonNull(sourceExperimentId, "sourceExperimentId must not be null");
        Objects.requireNonNull(reproducedExperiment, "reproducedExperiment must not be null");
    }
}
