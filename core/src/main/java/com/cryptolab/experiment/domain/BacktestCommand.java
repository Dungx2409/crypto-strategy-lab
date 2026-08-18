package com.cryptolab.experiment.domain;

import java.util.Objects;
import java.util.UUID;

public record BacktestCommand(
        UUID experimentId,
        UUID candidateId,
        MarketDatasetRef dataset,
        ExecutionConfig executionConfig) {

    public BacktestCommand {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(executionConfig, "executionConfig must not be null");
    }
}
