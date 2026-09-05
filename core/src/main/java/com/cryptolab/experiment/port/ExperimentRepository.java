package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.SortDirection;
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

    List<LeaderboardEntry> findLeaderboard(
            UUID searchRunId, int limit, LeaderboardSort sort, SortDirection direction);

    List<LeaderboardEntry> findAllTimeLeaderboard(int limit, LeaderboardSort sort, SortDirection direction);

    Optional<ExperimentDetails> findDetails(UUID experimentId);

    Optional<ExperimentPlan> findPlan(UUID experimentId);
}
