package com.cryptolab.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.account.application.AccountService;
import com.cryptolab.account.application.AuthenticationFailedException;
import com.cryptolab.account.domain.Account;
import com.cryptolab.account.domain.StoredAccount;
import com.cryptolab.account.port.AccountRepository;
import com.cryptolab.account.port.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultAccountSeederTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsTheConfiguredDefaultAccount() {
        AccountService accounts = service(new InMemoryAccounts());

        new DefaultAccountSeeder(accounts, true, "demo", "crypto-demo").run(null);

        assertThat(accounts.authenticate("demo", "crypto-demo").username()).isEqualTo("demo");
    }

    @Test
    void doesNothingWhenDefaultAccountIsDisabled() {
        AccountService accounts = service(new InMemoryAccounts());

        new DefaultAccountSeeder(accounts, false, "demo", "crypto-demo").run(null);

        assertThatThrownBy(() -> accounts.authenticate("demo", "crypto-demo"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void keepsAnExistingAccountAndPassword() {
        AccountService accounts = service(new InMemoryAccounts());
        accounts.register("demo", "original-password");

        new DefaultAccountSeeder(accounts, true, "demo", "crypto-demo").run(null);

        assertThat(accounts.authenticate("demo", "original-password").username()).isEqualTo("demo");
    }

    private static AccountService service(AccountRepository repository) {
        return new AccountService(repository, new PlainTestHasher(), CLOCK);
    }

    private static final class InMemoryAccounts implements AccountRepository {
        private final Map<String, StoredAccount> values = new HashMap<>();

        @Override
        public Optional<StoredAccount> findByNormalizedUsername(String normalizedUsername) {
            return Optional.ofNullable(values.get(normalizedUsername));
        }

        @Override
        public Account create(
                UUID id,
                String username,
                String normalizedUsername,
                String passwordHash,
                Instant createdAt) {
            Account account = new Account(id, username, createdAt);
            values.put(normalizedUsername, new StoredAccount(account, passwordHash));
            return account;
        }
    }

    private static final class PlainTestHasher implements PasswordHasher {
        @Override
        public String hash(String password) {
            return "hash:" + password;
        }

        @Override
        public boolean matches(String password, String passwordHash) {
            return passwordHash.equals(hash(password));
        }
    }
}
