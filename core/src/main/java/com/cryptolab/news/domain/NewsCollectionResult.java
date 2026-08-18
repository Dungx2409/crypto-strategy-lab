package com.cryptolab.news.domain;

import java.time.Instant;
import java.util.Objects;

public record NewsCollectionResult(
        int fetched,
        int stored,
        int analyzed,
        int inferenceFailures,
        NewsHealthStatus providerStatus,
        NewsHealthStatus sentimentStatus,
        Instant completedAt,
        String message) {

    public NewsCollectionResult {
        if (fetched < 0 || stored < 0 || analyzed < 0 || inferenceFailures < 0) {
            throw new IllegalArgumentException("collection counts must not be negative");
        }
        Objects.requireNonNull(providerStatus, "providerStatus must not be null");
        Objects.requireNonNull(sentimentStatus, "sentimentStatus must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
    }
}
