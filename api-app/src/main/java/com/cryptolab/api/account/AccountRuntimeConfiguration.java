package com.cryptolab.api.account;

import com.cryptolab.account.application.AccountService;
import com.cryptolab.account.port.AccountRepository;
import com.cryptolab.account.port.PasswordHasher;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AccountRuntimeConfiguration {

    @Bean
    AccountService accountService(
            AccountRepository accounts, PasswordHasher passwords, Clock marketDataClock) {
        return new AccountService(accounts, passwords, marketDataClock);
    }
}
