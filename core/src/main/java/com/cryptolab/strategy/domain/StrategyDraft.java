package com.cryptolab.strategy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StrategyDraft(
        UUID id,
        UUID accountId,
        String prompt,
        String idea,
        StrategyDraftStatus status,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt) {

    public StrategyDraft {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (prompt == null || prompt.isBlank() || prompt.length() > 4000) {
            throw new IllegalArgumentException("prompt must contain 1 to 4000 characters");
        }
        prompt = prompt.trim();
        if (idea == null || idea.isBlank()) {
            throw new IllegalArgumentException("idea must not be blank");
        }
        idea = idea.trim();
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
