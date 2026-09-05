package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStopReason;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

public interface SearchRunRepository {

    void create(SearchRun run, ExecutionConfig executionConfig);

    void transition(
            UUID searchRunId,
            SearchRunStatus expected,
            SearchRunStatus target,
            SearchStopReason stopReason,
            Instant at);

    void finishGeneration(UUID searchRunId, SearchStopReason stopReason, Instant at);

    int appendCandidatesAndCreateJobs(
            SearchRun run,
            ExecutionConfig executionConfig,
            JobDispatchMetadata dispatchMetadata,
            List<CandidateStrategy> candidates,
            Instant generatedAt);

    boolean cancel(UUID searchRunId, Instant cancelledAt);

    void recordEvaluation(UUID searchRunId, BigDecimal score);

    default Map<UUID, BigDecimal> awaitCandidateFitness(
            UUID searchRunId,
            List<UUID> candidateIds) {
        return Map.of();
    }

    void fail(UUID searchRunId, String failureCode, String failureMessage, Instant failedAt);

    Optional<SearchRunSummary> findSummary(UUID searchRunId);

    List<SearchRunSummary> findRecentSummaries(int limit);
}
