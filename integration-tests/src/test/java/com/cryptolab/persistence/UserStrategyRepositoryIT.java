package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.infrastructure.account.adapter.JdbcAccountRepository;
import com.cryptolab.infrastructure.strategy.adapter.JdbcUserStrategyRepository;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyDraft;
import com.cryptolab.strategy.domain.StrategyDraftStatus;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class UserStrategyRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("crypto_strategy_lab")
                    .withUsername("crypto_lab")
                    .withPassword("crypto_lab_test");

    private static final UUID ACCOUNT_ID = UUID.fromString("10000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_ACCOUNT_ID = UUID.fromString("10000000-0000-0000-0000-000000000102");
    private static JdbcUserStrategyRepository repository;

    @BeforeAll
    static void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcAccountRepository accounts = new JdbcAccountRepository(jdbc);
        Instant now = Instant.parse("2026-08-23T10:00:00Z");
        accounts.create(ACCOUNT_ID, "author", "author", "$2a$12$stored-hash", now);
        accounts.create(OTHER_ACCOUNT_ID, "other", "other", "$2a$12$stored-hash", now);
        repository = new JdbcUserStrategyRepository(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void persistsTestedPreviewThenPublishesItOnlyForItsAccount() {
        Instant now = Instant.parse("2026-08-23T10:00:00Z");
        UUID draftId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        String source = "BUY WHEN CLOSE > SMA(CLOSE, 20) SELL WHEN CLOSE < SMA(CLOSE, 20)";
        UserStrategyDocument document = new UserStrategyDocument(
                "Generated trend",
                "A generated strategy",
                List.of(new StrategyDefinition("AI_DSL", "1.0", Map.of("source", source))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO));
        repository.createDraft(new StrategyDraft(
                draftId, ACCOUNT_ID, "Generate a trend strategy", "Use an SMA crossover",
                StrategyDraftStatus.IDEA_PENDING_CONFIRMATION, null, null, now, now));

        repository.updateDraft(
                ACCOUNT_ID,
                draftId,
                StrategyDraftStatus.CODE_READY_FOR_CONFIRMATION,
                document,
                null,
                now.plusSeconds(1));

        assertThat(repository.findDraft(ACCOUNT_ID, draftId).orElseThrow().preview())
                .isEqualTo(document);
        assertThat(repository.findDraft(OTHER_ACCOUNT_ID, draftId)).isEmpty();
        assertThat(repository.findAll(ACCOUNT_ID)).isEmpty();

        var saved = repository.publishVersion(
                strategyId, ACCOUNT_ID, draftId, now.plusSeconds(2));

        assertThat(saved.document()).isEqualTo(document);
        assertThat(repository.findDraft(ACCOUNT_ID, draftId).orElseThrow().status())
                .isEqualTo(StrategyDraftStatus.READY);
        assertThat(repository.find(ACCOUNT_ID, strategyId)).contains(saved);
        assertThat(repository.find(OTHER_ACCOUNT_ID, strategyId)).isEmpty();
    }
}
