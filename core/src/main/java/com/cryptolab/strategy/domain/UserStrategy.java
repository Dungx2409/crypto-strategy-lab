package com.cryptolab.strategy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserStrategy(
        UUID id,
        UUID accountId,
        int version,
        UserStrategyDocument document,
        String sourcePrompt,
        Instant createdAt) {

    public UserStrategy {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
