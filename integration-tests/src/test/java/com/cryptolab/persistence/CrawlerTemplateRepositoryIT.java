package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.infrastructure.account.adapter.JdbcAccountRepository;
import com.cryptolab.infrastructure.news.adapter.JdbcCrawlerTemplateRepository;
import com.cryptolab.news.domain.CrawlerSelectors;
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
class CrawlerTemplateRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("crypto_strategy_lab")
                    .withUsername("crypto_lab")
                    .withPassword("crypto_lab_test");

    private static JdbcCrawlerTemplateRepository repository;
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
                accountId, "crawler", "crawler", "$2a$12$stored-hash",
                Instant.parse("2026-08-23T10:00:00Z"));
        repository = new JdbcCrawlerTemplateRepository(jdbc);
    }

    @Test
    void keepsRepairPendingUntilTheAccountConfirmsIt() {
        Instant now = Instant.parse("2026-08-23T10:00:00Z");
        UUID templateId = UUID.randomUUID();
        var original = new CrawlerSelectors("article", "h2", "a", "time");
        var repaired = new CrawlerSelectors(".story", ".headline", "a[href]", "time[datetime]");

        repository.create(templateId, accountId, "https://example.com/news", original, now);
        var pending = repository.addRepair(
                accountId, templateId, repaired, "article selector stopped matching", now.plusSeconds(60));

        assertThat(pending.version()).isEqualTo(2);
        assertThat(pending.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(repository.findCurrent(accountId, templateId).orElseThrow().selectors())
                .isEqualTo(original);

        var active = repository.activate(accountId, templateId, 2, now.plusSeconds(120));
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(active.selectors()).isEqualTo(repaired);
        assertThat(repository.findVersions(accountId, templateId))
                .extracting(item -> item.status())
                .containsExactly("ACTIVE", "HISTORICAL");
    }
}
