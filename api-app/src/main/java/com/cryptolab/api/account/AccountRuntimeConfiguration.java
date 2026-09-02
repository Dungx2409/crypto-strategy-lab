package com.cryptolab.api.account;

import com.cryptolab.account.application.AccountService;
import com.cryptolab.account.port.AccountRepository;
import com.cryptolab.account.port.PasswordHasher;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AccountRuntimeConfiguration {

    @Bean
    AccountService accountService(
            AccountRepository accounts, PasswordHasher passwords, Clock marketDataClock) {
        return new AccountService(accounts, passwords, marketDataClock);
    }

    @Bean
    ApplicationRunner defaultAccountSeeder(
            AccountService accounts,
            @Value("${crypto.accounts.default.enabled:false}") boolean enabled,
            @Value("${crypto.accounts.default.username:demo}") String username,
            @Value("${crypto.accounts.default.password:crypto-demo}") String password) {
        return new DefaultAccountSeeder(accounts, enabled, username, password);
    }
}
