package com.cryptolab.api.account;

import com.cryptolab.account.domain.Account;
import com.cryptolab.account.domain.AccountRole;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String username, AccountRole role, Instant createdAt) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.id(), account.username(), account.role(), account.createdAt());
    }
}
