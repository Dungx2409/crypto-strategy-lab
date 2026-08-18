package com.cryptolab.experiment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SearchRun(
        UUID id,
        SearchRunStatus status,
        SearchContext context,
        String generatorType,
        String generatorVersion,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        boolean cancelRequested) {

    public SearchRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(context, "context must not be null");
        if (!id.equals(context.searchRunId())) {
            throw new IllegalArgumentException("search run id must match context searchRunId");
        }
        if (generatorType == null || generatorType.isBlank()) {
            throw new IllegalArgumentException("generatorType must not be blank");
        }
        if (generatorVersion == null || generatorVersion.isBlank()) {
            throw new IllegalArgumentException("generatorVersion must not be blank");
        }
        generatorType = generatorType.trim();
        generatorVersion = generatorVersion.trim();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (startedAt != null && startedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("startedAt must not be before createdAt");
        }
        if (endedAt != null && (startedAt == null || endedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException("endedAt requires a start and must not be before startedAt");
        }
    }
}
