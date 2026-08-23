package com.cryptolab.account.domain;

import java.util.Objects;

public record StoredAccount(Account account, String passwordHash) {

    public StoredAccount {
        Objects.requireNonNull(account, "account must not be null");
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
    }
}
