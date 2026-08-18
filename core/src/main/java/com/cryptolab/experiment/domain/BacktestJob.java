package com.cryptolab.experiment.domain;

import java.util.Objects;

public record BacktestJob(BacktestCommand command, int attempt, String correlationId) {

    public BacktestJob {
        Objects.requireNonNull(command, "command must not be null");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
    }
}
