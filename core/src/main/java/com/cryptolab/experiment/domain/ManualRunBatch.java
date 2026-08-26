package com.cryptolab.experiment.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManualRunBatch(
        UUID id,
        UUID accountId,
        UUID strategyId,
        String symbol,
        Instant from,
        Instant to,
        ExecutionConfig executionConfig,
        ManualRunStatus status,
        boolean cancelRequested,
        Instant createdAt,
        Instant updatedAt,
        List<ManualRunChild> children) {

    public ManualRunBatch {
        children = List.copyOf(children);
    }
}
