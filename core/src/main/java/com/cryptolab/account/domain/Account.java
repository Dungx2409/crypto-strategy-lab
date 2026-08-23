package com.cryptolab.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Account(UUID id, String username, Instant createdAt) {

    public Account {
        Objects.requireNonNull(id, "id must not be null");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
