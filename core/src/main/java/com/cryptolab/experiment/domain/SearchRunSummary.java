package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record SearchRunSummary(
        SearchRun run,
        long generatedCandidates,
        long persistedCandidates,
        long pendingDispatchJobs,
        long queuedJobs,
        long runningJobs,
        long completedJobs,
        long failedJobs,
        long cancelledJobs,
        BigDecimal bestScore,
        int noImprovementIterations,
        SearchStopReason stopReason,
        String failureCode,
        String failureMessage) {

    public SearchRunSummary {
        Objects.requireNonNull(run, "run must not be null");
        if (generatedCandidates < 0 || persistedCandidates < 0 || persistedCandidates > generatedCandidates) {
            throw new IllegalArgumentException("candidate counts are inconsistent");
        }
        if (pendingDispatchJobs < 0 || queuedJobs < 0 || runningJobs < 0
                || completedJobs < 0 || failedJobs < 0 || cancelledJobs < 0
                || pendingDispatchJobs + queuedJobs + runningJobs + completedJobs
                        + failedJobs + cancelledJobs > persistedCandidates) {
            throw new IllegalArgumentException("job dispatch counts are inconsistent");
        }
        if (noImprovementIterations < 0) {
            throw new IllegalArgumentException("noImprovementIterations must not be negative");
        }
    }
}
