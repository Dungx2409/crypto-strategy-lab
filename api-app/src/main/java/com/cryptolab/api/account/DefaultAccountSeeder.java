package com.cryptolab.api.account;

import com.cryptolab.account.application.AccountConflictException;
import com.cryptolab.account.application.AccountService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

final class DefaultAccountSeeder implements ApplicationRunner {

    private final AccountService accounts;
    private final boolean enabled;
    private final String username;
    private final String password;

    DefaultAccountSeeder(
            AccountService accounts, boolean enabled, String username, String password) {
        this.accounts = accounts;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!enabled) {
            return;
        }
        try {
            accounts.register(username, password);
        } catch (AccountConflictException ignored) {
            // Keep the existing account and its password.
        }
    }
}
