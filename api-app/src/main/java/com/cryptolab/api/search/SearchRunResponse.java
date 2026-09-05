package com.cryptolab.api.search;

import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditions;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchRunResponse(
        UUID searchRunId,
        SearchRunStatus status,
        boolean cancelRequested,
        String generatorType,
        String generatorVersion,
        String strategySummary,
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
                run.status(),
                run.cancelRequested(),
                run.generatorType(),
                run.generatorVersion(),
                strategySummary(run),
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

    private static String strategySummary(com.cryptolab.experiment.domain.SearchRun run) {
        return run.context().strategyTypes().stream()
                .map(type -> {
                    String label = run.context().strategyLabels().getOrDefault(type, type);
                    String version = run.context().strategyVersions().get(type);
                    return label + "@" + version;
                })
                .reduce((left, right) -> left + " + " + right)
                .orElse("—");
    }
}
