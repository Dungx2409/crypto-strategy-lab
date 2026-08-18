package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.experiment.application.AsyncEvaluationService;
import com.cryptolab.experiment.application.AsyncRankingService;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.EventProcessingOutcome;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.experiment.port.AsyncEvaluationRepository;
import com.cryptolab.experiment.port.AsyncRankingRepository;
import com.cryptolab.infrastructure.experiment.adapter.JdbcAsyncEventRepository;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class AsyncEvaluationRankingIT {

    private static final UUID SEARCH_RUN_ID =
            UUID.fromString("74000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
            UUID.fromString("74000000-0000-0000-0000-000000000002");
    private static final UUID EXPERIMENT_ID =
            UUID.fromString("74000000-0000-0000-0000-000000000003");
    private static final UUID BACKTEST_EVENT_ID =
            UUID.fromString("74000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-08-18T16:00:00Z");
    private static final EvaluationMetrics METRICS = new EvaluationMetrics(
            new BigDecimal("12.500000000000000000"),
            new BigDecimal("-3.000000000000000000"),
            7,
            new BigDecimal("9.500000000000000000"));

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    private static JdbcTemplate jdbc;
    private static ObjectMapper objectMapper;
    private static AsyncEvaluationService evaluation;
    private static AsyncRankingService ranking;

    @BeforeAll
    static void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        JdbcAsyncEventRepository target = new JdbcAsyncEventRepository(jdbc, objectMapper);
        AsyncEvaluationRepository evaluationRepository =
                transactional(target, AsyncEvaluationRepository.class, transactionManager);
        AsyncRankingRepository rankingRepository =
                transactional(target, AsyncRankingRepository.class, transactionManager);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        evaluation = new AsyncEvaluationService(evaluationRepository, clock);
        ranking = new AsyncRankingService(rankingRepository, new DefaultRankingService(), clock);
        seedCompletedExperiment();
    }

    @Test
    void duplicateDeliveryCannotDuplicateEvaluationOutboxOrLeaderboardProjection() throws Exception {
        DomainEventEnvelope<BacktestCompletedEvent> completed = completedEvent(BACKTEST_EVENT_ID);

        assertThat(evaluation.process(completed)).isEqualTo(EventProcessingOutcome.PROCESSED);
        assertThat(evaluation.process(completed)).isEqualTo(EventProcessingOutcome.DUPLICATE);
        assertThat(countProcessed("async-evaluation", BACKTEST_EVENT_ID)).isOne();
        assertThat(countOutbox("StrategyEvaluated")).isOne();

        DomainEventEnvelope<StrategyEvaluatedEvent> evaluated = readStrategyEvaluatedOutbox();
        assertThat(ranking.process(evaluated)).isEqualTo(EventProcessingOutcome.PROCESSED);
        assertThat(ranking.process(evaluated)).isEqualTo(EventProcessingOutcome.DUPLICATE);

        assertThat(countProcessed("async-ranking", evaluated.eventId())).isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM leaderboard_entries WHERE search_run_id = ?",
                        Integer.class,
                        SEARCH_RUN_ID))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT experiment_id FROM leaderboard_entries WHERE search_run_id = ? AND rank = 1",
                        UUID.class,
                        SEARCH_RUN_ID))
                .isEqualTo(EXPERIMENT_ID);
        assertThat(countOutbox("LeaderboardUpdated")).isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT best_score FROM search_runs WHERE id = ?", BigDecimal.class, SEARCH_RUN_ID))
                .isEqualByComparingTo(METRICS.score());
    }

    @Test
    void failedProcessingRollsBackInboxMarker() {
        UUID missingExperiment = UUID.fromString("74000000-0000-0000-0000-000000000099");
        UUID eventId = UUID.fromString("74000000-0000-0000-0000-000000000098");
        DomainEventEnvelope<BacktestCompletedEvent> invalid = new DomainEventEnvelope<>(
                eventId,
                "BacktestCompleted",
                1,
                NOW,
                "Experiment",
                missingExperiment,
                "missing",
                null,
                missingExperiment.toString(),
                new BacktestCompletedEvent(
                        missingExperiment, SEARCH_RUN_ID, METRICS, "evaluator-v1", NOW));

        assertThatThrownBy(() -> evaluation.process(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metrics not found");
        assertThat(countProcessed("async-evaluation", eventId)).isZero();
    }

    private static DomainEventEnvelope<BacktestCompletedEvent> completedEvent(UUID eventId) {
        return new DomainEventEnvelope<>(
                eventId,
                "BacktestCompleted",
                1,
                NOW.minusSeconds(1),
                "Experiment",
                EXPERIMENT_ID,
                SEARCH_RUN_ID.toString(),
                null,
                EXPERIMENT_ID.toString(),
                new BacktestCompletedEvent(
                        EXPERIMENT_ID, SEARCH_RUN_ID, METRICS, "evaluator-v1", NOW.minusSeconds(1)));
    }

    @SuppressWarnings("unchecked")
    private static DomainEventEnvelope<StrategyEvaluatedEvent> readStrategyEvaluatedOutbox()
            throws Exception {
        String json = jdbc.queryForObject(
                "SELECT payload_json::text FROM outbox_events WHERE event_type = 'StrategyEvaluated'",
                String.class);
        JavaType type = objectMapper
                .getTypeFactory()
                .constructParametricType(DomainEventEnvelope.class, StrategyEvaluatedEvent.class);
        return objectMapper.readValue(json, type);
    }

    private static int countProcessed(String consumer, UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_name = ? AND event_id = ?",
                Integer.class,
                consumer,
                eventId);
    }

    private static int countOutbox(String eventType) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = ?", Integer.class, eventType);
    }

    private static void seedCompletedExperiment() {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(NOW.minusSeconds(10), ZoneOffset.UTC);
        jdbc.update(
                """
                INSERT INTO search_runs (
                    id, status, symbol, timeframe, generator_type, generator_version,
                    search_config_json, stop_conditions_json, execution_config_json, created_at, started_at
                ) VALUES (?, 'RUNNING', 'BTCUSDT', '5m', 'random', '1.0', '{}'::jsonb,
                          '{}'::jsonb, '{}'::jsonb, ?, ?)
                """,
                SEARCH_RUN_ID,
                timestamp,
                timestamp);
        jdbc.update(
                """
                INSERT INTO candidates (id, search_run_id, candidate_hash, candidate_spec_json, created_at)
                VALUES (?, ?, 'candidate-hash', '{}'::jsonb, ?)
                """,
                CANDIDATE_ID,
                SEARCH_RUN_ID,
                timestamp);
        jdbc.update(
                """
                INSERT INTO experiments (
                    id, candidate_id, search_run_id, status, dataset_ref_json,
                    execution_config_json, strategy_snapshot_json, combination_policy_json,
                    generator_snapshot_json, evaluator_version, code_commit, build_version,
                    completed_at, version
                ) VALUES (?, ?, ?, 'COMPLETED', '{}'::jsonb, '{}'::jsonb, '{}'::jsonb,
                          '{}'::jsonb, '{}'::jsonb, 'evaluator-v1', 'test', 'test', ?, 1)
                """,
                EXPERIMENT_ID,
                CANDIDATE_ID,
                SEARCH_RUN_ID,
                timestamp);
        jdbc.update(
                """
                INSERT INTO evaluation_metrics (
                    experiment_id, total_return_pct, max_drawdown_pct,
                    total_trades, score, metrics_json
                ) VALUES (?, ?, ?, ?, ?, '{}'::jsonb)
                """,
                EXPERIMENT_ID,
                METRICS.totalReturnPct(),
                METRICS.maxDrawdownPct(),
                METRICS.totalTrades(),
                METRICS.score());
    }

    @SuppressWarnings("unchecked")
    private static <T> T transactional(
            Object target,
            Class<T> contract,
            DataSourceTransactionManager transactionManager) {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setInterfaces(contract);
        factory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
