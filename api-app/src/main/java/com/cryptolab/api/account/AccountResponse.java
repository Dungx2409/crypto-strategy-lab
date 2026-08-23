package com.cryptolab.api.account;

import com.cryptolab.account.domain.Account;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String username, Instant createdAt) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.id(), account.username(), account.createdAt());
    }
}
