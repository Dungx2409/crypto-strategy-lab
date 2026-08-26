package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.marketdata.domain.Timeframe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentRepository {

    void create(ExperimentPlan plan);

    void transition(UUID experimentId, ExperimentStatus expected, ExperimentStatus target, Instant at);

    void complete(UUID experimentId, BacktestResult result, Evaluation evaluation, Instant completedAt);

    void fail(UUID experimentId, String failureCode, String failureMessage, Instant failedAt);

    List<Evaluation> findCompletedEvaluations(UUID searchRunId);

    void replaceLeaderboard(UUID searchRunId, List<Ranking> rankings, Instant updatedAt);

    List<LeaderboardEntry> findLeaderboard(UUID searchRunId, int limit);

    Optional<ExperimentDetails> findDetails(UUID experimentId);

    Optional<ExperimentPlan> findPlan(UUID experimentId);

    default boolean isExperimentOwnedBy(UUID experimentId, UUID accountId) {
        return false;
    }

    default boolean isSearchRunOwnedBy(UUID searchRunId, UUID accountId) {
        return false;
    }

    default List<LeaderboardEntry> findPublicDiscoveryLeaderboard(
            String symbol, Timeframe timeframe, int limit) {
        return List.of();
    }
}
