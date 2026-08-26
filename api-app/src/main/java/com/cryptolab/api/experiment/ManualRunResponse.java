package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.ManualRunBatch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManualRunResponse(
        UUID id,
        UUID strategyId,
        String symbol,
        Instant from,
        Instant to,
        ExecutionConfig executionConfig,
        String status,
        boolean cancelRequested,
        Instant createdAt,
        Instant updatedAt,
        List<Child> children) {

    static ManualRunResponse from(ManualRunBatch batch) {
        return new ManualRunResponse(
                batch.id(),
                batch.strategyId(),
                batch.symbol(),
                batch.from(),
                batch.to(),
                batch.executionConfig(),
                batch.status().name(),
                batch.cancelRequested(),
                batch.createdAt(),
                batch.updatedAt(),
                batch.children().stream()
                        .map(child -> new Child(
                                child.id(),
                                child.timeframe().exchangeCode(),
                                child.status().name(),
                                child.experimentId(),
                                child.failureMessage()))
                        .toList());
    }

    public record Child(
            UUID id,
            String timeframe,
            String status,
            UUID experimentId,
            String failureMessage) {}
}
