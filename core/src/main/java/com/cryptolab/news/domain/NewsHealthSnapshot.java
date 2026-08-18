package com.cryptolab.news.domain;

import java.time.Instant;
import java.util.Objects;

public record NewsHealthSnapshot(
        NewsHealthStatus providerStatus,
        NewsHealthStatus sentimentStatus,
        Instant lastCollectionAt,
        String lastError) {

    public NewsHealthSnapshot {
        Objects.requireNonNull(providerStatus, "providerStatus must not be null");
        Objects.requireNonNull(sentimentStatus, "sentimentStatus must not be null");
    }
}
