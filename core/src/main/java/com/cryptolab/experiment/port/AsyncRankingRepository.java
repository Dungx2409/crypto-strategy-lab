package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AsyncRankingRepository {

    List<Evaluation> findCompletedEvaluations(UUID searchRunId);

    boolean replaceLeaderboardOnce(
            DomainEventEnvelope<StrategyEvaluatedEvent> sourceEvent,
            List<Ranking> rankings,
            DomainEventEnvelope<LeaderboardUpdatedEvent> resultEvent,
            Instant processedAt);
}
