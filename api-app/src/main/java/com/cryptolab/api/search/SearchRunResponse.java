package com.cryptolab.api.search;

import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunKind;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditions;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchRunResponse(
        UUID searchRunId,
        SearchRunKind runKind,
        SearchRunStatus status,
        boolean cancelRequested,
        String generatorType,
        String generatorVersion,
        long randomSeed,
        int batchSize,
        StopConditions stopConditions,
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
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        String failureCode,
        String failureMessage) {

    static SearchRunResponse from(SearchRunSummary summary) {
        var run = summary.run();
        return new SearchRunResponse(
                run.id(),
                run.runKind(),
                run.status(),
                run.cancelRequested(),
                run.generatorType(),
                run.generatorVersion(),
                run.context().randomSeed(),
                run.context().batchSize(),
                run.context().stopConditions(),
                summary.generatedCandidates(),
                summary.persistedCandidates(),
                summary.pendingDispatchJobs(),
                summary.queuedJobs(),
                summary.runningJobs(),
                summary.completedJobs(),
                summary.failedJobs(),
                summary.cancelledJobs(),
                summary.bestScore(),
                summary.noImprovementIterations(),
                summary.stopReason(),
                run.createdAt(),
                run.startedAt(),
                run.endedAt(),
                summary.failureCode(),
                summary.failureMessage());
    }
}
