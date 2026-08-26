package com.cryptolab.api.account;

import com.cryptolab.account.application.AccountService;
import com.cryptolab.account.port.AccountRepository;
import com.cryptolab.account.port.PasswordHasher;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AccountRuntimeConfiguration {

    @Bean
    AccountService accountService(
            AccountRepository accounts,
            PasswordHasher passwords,
            Clock marketDataClock,
            @Value("${crypto.security.admin-usernames:}") String adminUsernames) {
        Set<String> normalizedAdmins = Arrays.stream(adminUsernames.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return new AccountService(accounts, passwords, marketDataClock, normalizedAdmins);
    }
}
