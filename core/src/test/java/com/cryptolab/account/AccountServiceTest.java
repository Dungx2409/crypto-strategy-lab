package com.cryptolab.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.account.application.AccountConflictException;
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

class AccountServiceTest {

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final PlainTestHasher passwords = new PlainTestHasher();
    private final AccountService service = new AccountService(
            accounts,
            passwords,
            Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void registersAndAuthenticatesCaseInsensitiveUsername() {
        Account registered = service.register("First.Student", "password123");

        Account authenticated = service.authenticate("first.student", "password123");

        assertThat(authenticated).isEqualTo(registered);
        assertThat(registered.createdAt()).isEqualTo(Instant.parse("2026-08-23T10:00:00Z"));
        assertThat(accounts.values.get("first.student").passwordHash()).isNotEqualTo("password123");
    }

    @Test
    void rejectsDuplicateUsernameAndWrongPassword() {
        service.register("student", "password123");

        assertThatThrownBy(() -> service.register("STUDENT", "different123"))
                .isInstanceOf(AccountConflictException.class);
        assertThatThrownBy(() -> service.authenticate("student", "wrong-pass"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void validatesUsernameAndPasswordBeforePersistence() {
        assertThatThrownBy(() -> service.register("x", "password123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("student", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.values).isEmpty();
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
