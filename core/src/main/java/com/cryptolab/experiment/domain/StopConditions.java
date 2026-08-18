package com.cryptolab.experiment.domain;

import java.time.Duration;

public record StopConditions(
        Long maxCandidates,
        Duration maxDuration,
        Integer noImprovementIterations) {

    public StopConditions {
        if (maxCandidates == null && maxDuration == null && noImprovementIterations == null) {
            throw new IllegalArgumentException("At least one automatic stop condition is required");
        }
        if (maxCandidates != null && maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }
        if (maxDuration != null && (maxDuration.isZero() || maxDuration.isNegative())) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
        if (noImprovementIterations != null && noImprovementIterations <= 0) {
            throw new IllegalArgumentException("noImprovementIterations must be positive");
        }
    }
}
