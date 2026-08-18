package com.cryptolab.experiment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LeaderboardUpdatedEvent(
        UUID searchRunId,
        List<Ranking> rankings,
        Instant updatedAt) {

    public LeaderboardUpdatedEvent {
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        rankings = List.copyOf(Objects.requireNonNull(rankings, "rankings must not be null"));
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
