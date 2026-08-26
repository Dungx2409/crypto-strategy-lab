package com.cryptolab.account.port;

import com.cryptolab.account.domain.Account;
import com.cryptolab.account.domain.AccountRole;
import com.cryptolab.account.domain.StoredAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<StoredAccount> findByNormalizedUsername(String normalizedUsername);

    Account create(
            UUID id,
            String username,
            String normalizedUsername,
            String passwordHash,
            AccountRole role,
            Instant createdAt);
}
