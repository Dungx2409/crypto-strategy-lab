package com.cryptolab.account.application;

import com.cryptolab.account.domain.Account;
import com.cryptolab.account.domain.AccountRole;
import com.cryptolab.account.domain.StoredAccount;
import com.cryptolab.account.port.AccountRepository;
import com.cryptolab.account.port.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AccountService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,64}");

    private final AccountRepository accounts;
    private final PasswordHasher passwords;
    private final Clock clock;
    private final Set<String> adminUsernames;

    public AccountService(AccountRepository accounts, PasswordHasher passwords, Clock clock) {
        this(accounts, passwords, clock, Set.of());
    }

    public AccountService(
            AccountRepository accounts,
            PasswordHasher passwords,
            Clock clock,
            Set<String> adminUsernames) {
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
        this.passwords = Objects.requireNonNull(passwords, "passwords must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.adminUsernames = Set.copyOf(
                Objects.requireNonNull(adminUsernames, "adminUsernames must not be null"));
    }

    public Account register(String username, String password) {
        String displayName = requireUsername(username);
        requirePassword(password);
        String normalized = normalize(displayName);
        if (accounts.findByNormalizedUsername(normalized).isPresent()) {
            throw new AccountConflictException("Username is already registered");
        }
        Instant now = clock.instant();
        return accounts.create(
                UUID.randomUUID(),
                displayName,
                normalized,
                passwords.hash(password),
                adminUsernames.contains(normalized) ? AccountRole.ADMIN : AccountRole.USER,
                now);
    }

    public Account authenticate(String username, String password) {
        String normalized = normalize(requireUsername(username));
        requirePassword(password);
        StoredAccount stored = accounts.findByNormalizedUsername(normalized)
                .orElseThrow(AuthenticationFailedException::new);
        if (!passwords.matches(password, stored.passwordHash())) {
            throw new AuthenticationFailedException();
        }
        return stored.account();
    }

    private static String requireUsername(String username) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Username must contain 3 to 64 letters, numbers, dots, underscores, or hyphens");
        }
        return username.trim();
    }

    private static void requirePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Password must contain 8 to 128 characters");
        }
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
