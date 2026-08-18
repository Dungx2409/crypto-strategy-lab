package com.cryptolab.experiment.domain;

import java.util.Objects;
import java.util.UUID;

public record LeaderboardEntry(
        UUID searchRunId,
        Ranking ranking,
        String strategySummary) {

    public LeaderboardEntry {
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        Objects.requireNonNull(ranking, "ranking must not be null");
        if (strategySummary == null || strategySummary.isBlank()) {
            throw new IllegalArgumentException("strategySummary must not be blank");
        }
    }
}
