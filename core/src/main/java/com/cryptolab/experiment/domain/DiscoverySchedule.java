package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DiscoverySchedule(
        UUID id,
        UUID accountId,
        String symbol,
        Timeframe timeframe,
        Duration lookback,
        BigDecimal initialCapital,
        long candidateLimit,
        Duration interval,
        DiscoveryScheduleStatus status,
        Instant nextRunAt,
        UUID activeSearchRunId,
        long completedRuns,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public DiscoverySchedule {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        symbol = symbol.trim().toUpperCase();
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        if (lookback == null || lookback.isZero() || lookback.isNegative()) {
            throw new IllegalArgumentException("lookback must be positive");
        }
        if (initialCapital == null || initialCapital.signum() <= 0) {
            throw new IllegalArgumentException("initialCapital must be positive");
        }
        if (candidateLimit < 1) {
            throw new IllegalArgumentException("candidateLimit must be positive");
        }
        if (interval == null || interval.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("interval must be at least one minute");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(nextRunAt, "nextRunAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (completedRuns < 0) {
            throw new IllegalArgumentException("completedRuns must not be negative");
        }
    }
}
