package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SearchProgress(
        Instant startedAt,
        Instant observedAt,
        long generatedCandidates,
        long persistedCandidates,
        BigDecimal bestScore,
        int noImprovementIterations) {

    public SearchProgress {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (observedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("observedAt must not be before startedAt");
        }
        if (generatedCandidates < 0 || persistedCandidates < 0 || persistedCandidates > generatedCandidates) {
            throw new IllegalArgumentException("candidate counts are inconsistent");
        }
        if (noImprovementIterations < 0) {
            throw new IllegalArgumentException("noImprovementIterations must not be negative");
        }
    }
}
