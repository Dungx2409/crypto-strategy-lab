package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.domain.DiscoverySchedule;
import com.cryptolab.experiment.domain.DiscoveryScheduleStatus;
import com.cryptolab.infrastructure.account.adapter.JdbcAccountRepository;
import com.cryptolab.infrastructure.experiment.adapter.JdbcDiscoveryScheduleRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Duration;
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
class DiscoveryScheduleRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("crypto_strategy_lab")
                    .withUsername("crypto_lab")
                    .withPassword("crypto_lab_test");

    private static JdbcDiscoveryScheduleRepository repository;
    private static UUID accountId;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        accountId = UUID.randomUUID();
        new JdbcAccountRepository(jdbc).create(
                accountId, "scheduler", "scheduler", "$2a$12$stored-hash",
                Instant.parse("2026-08-23T10:00:00Z"));
        repository = new JdbcDiscoveryScheduleRepository(jdbc);
    }

    @Test
    void claimsDueRunOnceAndRecoversItAfterRestart() {
        Instant now = Instant.parse("2026-08-23T10:00:00Z");
        UUID scheduleId = UUID.randomUUID();
        UUID searchRunId = UUID.randomUUID();
        repository.create(new DiscoverySchedule(
                scheduleId, accountId, "BTCUSDT", Timeframe.H1, Duration.ofDays(30),
                new BigDecimal("10000"), 100, Duration.ofHours(24),
                DiscoveryScheduleStatus.ACTIVE, now, null, null, 0, null, now, now));

        assertThat(repository.findDue(now, 10)).extracting(DiscoverySchedule::id).contains(scheduleId);
        assertThat(repository.claim(scheduleId, searchRunId, now.plus(Duration.ofHours(24)), now)).isTrue();
        assertThat(repository.claim(scheduleId, UUID.randomUUID(), now.plus(Duration.ofHours(24)), now)).isFalse();
        assertThat(repository.find(accountId, scheduleId).orElseThrow().activeSearchRunId())
                .isEqualTo(searchRunId);
        assertThat(repository.find(accountId, scheduleId).orElseThrow().lastSearchRunId())
                .isEqualTo(searchRunId);

        repository.recoverInterrupted(now.plusSeconds(60));

        DiscoverySchedule recovered = repository.find(accountId, scheduleId).orElseThrow();
        assertThat(recovered.activeSearchRunId()).isNull();
        assertThat(recovered.nextRunAt()).isEqualTo(now.plusSeconds(60));
        assertThat(recovered.lastError()).contains("restarted");
    }

    @Test
    void recordsEveryConfigurationVersion() {
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        UUID scheduleId = UUID.randomUUID();
        repository.create(new DiscoverySchedule(
                scheduleId, accountId, "BTCUSDT", Timeframe.H1, Duration.ofDays(30),
                new BigDecimal("10000"), 100, Duration.ofHours(24),
                DiscoveryScheduleStatus.ACTIVE, now, null, null, 0, null, now, now));

        repository.updateConfiguration(
                accountId, scheduleId, "ETHUSDT", Timeframe.H4, Duration.ofDays(90),
                new BigDecimal("25000"), 250, Duration.ofHours(12), now.plusSeconds(60));

        var versions = repository.findVersions(accountId, scheduleId);
        assertThat(versions).hasSize(2);
        assertThat(versions.getFirst().version()).isEqualTo(2);
        assertThat(versions.getFirst().symbol()).isEqualTo("ETHUSDT");
        assertThat(versions.getFirst().timeframe()).isEqualTo(Timeframe.H4);
        assertThat(versions.getLast().version()).isEqualTo(1);
    }
}
