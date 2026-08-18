package com.cryptolab.experiment.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class BacktestJobIdentifiers {

    private BacktestJobIdentifiers() {}

    public static UUID experimentId(UUID searchRunId, UUID candidateId) {
        return deterministic("experiment", searchRunId, candidateId);
    }

    public static UUID dispatchEventId(UUID searchRunId, UUID candidateId) {
        return deterministic("backtest-dispatch", searchRunId, candidateId);
    }

    private static UUID deterministic(String purpose, UUID searchRunId, UUID candidateId) {
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        String name = purpose + ":" + searchRunId + ":" + candidateId;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
