package com.cryptolab.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BacktestResult(
        UUID experimentId,
        UUID candidateId,
        List<Trade> trades,
        List<RecordedSignal> signals,
        List<EquityPoint> equityCurve,
        BigDecimal endingCapital,
        Instant startedAt,
        Instant completedAt,
        String engineVersion) {

    public BacktestResult {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        trades = List.copyOf(Objects.requireNonNull(trades, "trades must not be null"));
        signals = List.copyOf(Objects.requireNonNull(signals, "signals must not be null"));
        equityCurve = List.copyOf(Objects.requireNonNull(equityCurve, "equityCurve must not be null"));
        if (equityCurve.isEmpty()) {
            throw new IllegalArgumentException("equityCurve must not be empty");
        }
        Objects.requireNonNull(endingCapital, "endingCapital must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        if (engineVersion == null || engineVersion.isBlank()) {
            throw new IllegalArgumentException("engineVersion must not be blank");
        }
    }
}
