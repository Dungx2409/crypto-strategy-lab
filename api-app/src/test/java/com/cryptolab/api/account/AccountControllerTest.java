package com.cryptolab.api.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.account.application.AccountService;
import com.cryptolab.account.domain.Account;
import com.cryptolab.account.domain.AccountRole;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AccountService service = new AccountService(
                new InMemoryAccounts(), new PlainTestHasher(), CLOCK);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(service))
                .setControllerAdvice(new AccountApiExceptionHandler(CLOCK))
                .build();
    }

    @Test
    void registrationCreatesASessionAndCurrentAccountReturnsItsIdentity() throws Exception {
        var registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"student","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("student"))
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) registration.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student"));
    }

    @Test
    void loginRejectsWrongCredentialsWithoutRevealingWhichFieldFailed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"student","password":"password123"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"student","password":"wrong-pass"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Username or password is incorrect"));
    }

    @Test
    void currentAccountRequiresLoginAndLogoutInvalidatesTheSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        var registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"student","password":"password123"}
                                """))
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) registration.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isNoContent());
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
                AccountRole role,
                Instant createdAt) {
            Account account = new Account(id, username, role, createdAt);
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
