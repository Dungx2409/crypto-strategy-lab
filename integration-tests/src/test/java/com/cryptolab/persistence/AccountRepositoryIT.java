package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.account.application.AccountConflictException;
import com.cryptolab.account.domain.AccountRole;
import com.cryptolab.infrastructure.account.adapter.JdbcAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class AccountRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("crypto_strategy_lab")
                    .withUsername("crypto_lab")
                    .withPassword("crypto_lab_test");

    private static JdbcAccountRepository repository;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repository = new JdbcAccountRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void storesCredentialsAndEnforcesNormalizedUsernameUniqueness() {
        Instant createdAt = Instant.parse("2026-08-23T10:00:00Z");
        UUID id = UUID.randomUUID();

        repository.create(
                id, "Student", "student", "$2a$12$stored-hash", AccountRole.USER, createdAt);

        var stored = repository.findByNormalizedUsername("student").orElseThrow();
        assertThat(stored.account().id()).isEqualTo(id);
        assertThat(stored.account().username()).isEqualTo("Student");
        assertThat(stored.account().role()).isEqualTo(AccountRole.USER);
        assertThat(stored.passwordHash()).isEqualTo("$2a$12$stored-hash");
        assertThatThrownBy(() -> repository.create(
                        UUID.randomUUID(),
                        "STUDENT",
                        "student",
                        "$2a$12$other-hash",
                        AccountRole.USER,
                        createdAt))
                .isInstanceOf(AccountConflictException.class);
    }
}
