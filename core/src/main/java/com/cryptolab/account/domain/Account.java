package com.cryptolab.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Account(UUID id, String username, AccountRole role, Instant createdAt) {

    public Account {
        Objects.requireNonNull(id, "id must not be null");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
