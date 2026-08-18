package com.cryptolab.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.infrastructure.experiment.adapter.JdbcSearchRunRepository;
import com.cryptolab.infrastructure.experiment.messaging.BacktestJobTopology;
import com.cryptolab.infrastructure.experiment.messaging.JdbcBacktestJobOutboxRepository;
import com.cryptolab.infrastructure.experiment.messaging.RabbitBacktestJobOutboxPublisher;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BacktestJobOutboxIT {

    private static final Instant NOW = Instant.parse("2026-08-18T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID DATASET_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final MarketDatasetRef DATASET = new MarketDatasetRef(
            "BTCUSDT",
            Timeframe.M5,
            Instant.parse("2026-08-18T00:00:00Z"),
            Instant.parse("2026-08-18T01:00:00Z"),
            "dispatch-it-v1",
            "dispatch-checksum-v1");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
                    DockerImageName.parse("rabbitmq:4.1-management-alpine"))
            .withAdminUser("crypto_lab")
            .withAdminPassword("crypto_lab_test");

    private static JdbcTemplate jdbc;
    private static JdbcSearchRunRepository searchRuns;
    private static JdbcBacktestJobOutboxRepository outbox;
    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                """
                INSERT INTO market_datasets (
                    id, symbol, timeframe, from_time, to_time, dataset_version, checksum, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                DATASET_ID,
                DATASET.symbol(),
                DATASET.timeframe().exchangeCode(),
                timestamp(DATASET.from()),
                timestamp(DATASET.to()),
                DATASET.datasetVersion(),
                DATASET.checksum(),
                timestamp(NOW));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        searchRuns = new JdbcSearchRunRepository(jdbc, mapper);
        outbox = new JdbcBacktestJobOutboxRepository(jdbc);

        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        declareTopology();
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @Order(1)
    void reportsPendingUntilRabbitPublisherConfirmThenAtomicallyMarksQueued() {
        UUID searchRunId = UUID.fromString("60000000-0000-0000-0000-000000000010");
        UUID experimentId = persistOneJob(searchRunId, 10);

        assertThat(searchRuns.findSummary(searchRunId).orElseThrow().pendingDispatchJobs()).isEqualTo(1);
        assertThat(searchRuns.findSummary(searchRunId).orElseThrow().queuedJobs()).isZero();
        assertStatus(experimentId, "CREATED", "PENDING_DISPATCH", false);

        RabbitBacktestJobOutboxPublisher publisher = publisher("publisher-success");
        assertThat(publisher.publishAvailable()).isEqualTo(1);

        Message delivered = rabbitTemplate.receive(BacktestJobTopology.JOB_QUEUE, 5_000);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(delivered.getMessageProperties().getHeader("experimentId").toString())
                .isEqualTo(experimentId.toString());
        assertThat(searchRuns.findSummary(searchRunId).orElseThrow().pendingDispatchJobs()).isZero();
        assertThat(searchRuns.findSummary(searchRunId).orElseThrow().queuedJobs()).isEqualTo(1);
        assertStatus(experimentId, "QUEUED", "QUEUED", true);
    }

    @Test
    @Order(2)
    void unroutablePublishRemainsPendingAndIsRetriedRatherThanReportedQueued() {
        UUID searchRunId = UUID.fromString("60000000-0000-0000-0000-000000000020");
        UUID experimentId = persistOneJob(searchRunId, 20);
        jdbc.update(
                "UPDATE outbox_events SET routing_key = 'missing.route' WHERE aggregate_id = ?",
                experimentId);

        RabbitBacktestJobOutboxPublisher publisher = publisher("publisher-unroutable");
        assertThat(publisher.publishAvailable()).isZero();

        assertStatus(experimentId, "CREATED", "PENDING_DISPATCH", false);
        assertThat(searchRuns.findSummary(searchRunId).orElseThrow().queuedJobs()).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT attempt_count FROM outbox_events WHERE aggregate_id = ?",
                        Integer.class,
                        experimentId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT last_error FROM outbox_events WHERE aggregate_id = ?",
                        String.class,
                        experimentId))
                .containsIgnoringCase("unroutable");
    }

    @Test
    @Order(3)
    void cancellationTombstonesPendingOutboxSoItCannotBeDispatchedLater() {
        UUID searchRunId = UUID.fromString("60000000-0000-0000-0000-000000000030");
        UUID experimentId = persistOneJob(searchRunId, 30);

        assertThat(searchRuns.cancel(searchRunId, NOW.plusSeconds(1))).isTrue();
        assertThat(publisher("publisher-after-cancel").publishAvailable()).isZero();

        assertThat(rabbitTemplate.receive(BacktestJobTopology.JOB_QUEUE, 250)).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM experiments WHERE id = ?", String.class, experimentId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM backtest_jobs WHERE experiment_id = ?",
                        String.class,
                        experimentId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                        "SELECT cancelled_at IS NOT NULL FROM outbox_events WHERE aggregate_id = ?",
                        Boolean.class,
                        experimentId))
                .isTrue();
    }

    private static UUID persistOneJob(UUID searchRunId, int candidateNumber) {
        SearchContext context = new SearchContext(
                searchRunId,
                DATASET,
                List.of("MA"),
                Map.of("MA", "1.0"),
                new SearchParameterSpace(Map.of("MA", Map.of("fastPeriod", List.of(10, 20)))),
                policy(),
                1234L,
                new StopConditions(1L, null, null),
                1);
        SearchRun created = new SearchRun(
                searchRunId,
                SearchRunStatus.CREATED,
                context,
                "random",
                "1.0",
                NOW,
                null,
                null,
                false);
        searchRuns.create(created, executionConfig());
        searchRuns.transition(searchRunId, SearchRunStatus.CREATED, SearchRunStatus.RUNNING, null, NOW);
        SearchRun running = searchRuns.findSummary(searchRunId).orElseThrow().run();
        CandidateStrategy candidate = candidate(candidateNumber);
        assertThat(searchRuns.appendCandidatesAndCreateJobs(
                        running,
                        executionConfig(),
                        new JobDispatchMetadata("score-v1", "it-commit", "it-build"),
                        List.of(candidate),
                        NOW))
                .isEqualTo(1);
        return jdbc.queryForObject(
                "SELECT experiment_id FROM backtest_jobs WHERE search_run_id = ?",
                UUID.class,
                searchRunId);
    }

    private static void assertStatus(
            UUID experimentId,
            String experimentStatus,
            String jobStatus,
            boolean published) {
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM experiments WHERE id = ?", String.class, experimentId))
                .isEqualTo(experimentStatus);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM backtest_jobs WHERE experiment_id = ?",
                        String.class,
                        experimentId))
                .isEqualTo(jobStatus);
        assertThat(jdbc.queryForObject(
                        "SELECT published_at IS NOT NULL FROM outbox_events WHERE aggregate_id = ?",
                        Boolean.class,
                        experimentId))
                .isEqualTo(published);
    }

    private static RabbitBacktestJobOutboxPublisher publisher(String publisherId) {
        return new RabbitBacktestJobOutboxPublisher(
                outbox, rabbitTemplate, CLOCK, publisherId, 10, Duration.ofSeconds(5));
    }

    private static CandidateStrategy candidate(int value) {
        List<StrategyDefinition> strategies = List.of(
                new StrategyDefinition("MA", "1.0", Map.of("fastPeriod", value, "slowPeriod", 50)));
        return new CandidateStrategy(
                new UUID(0, value),
                strategies,
                policy(),
                CandidateCanonicalizer.hash(strategies, policy()));
    }

    private static CombinationPolicyDefinition policy() {
        return new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO);
    }

    private static ExecutionConfig executionConfig() {
        return new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                false,
                "NEXT_CANDLE_OPEN",
                "deterministic-next-open-v1");
    }

    private static void declareTopology() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        DirectExchange jobs = new DirectExchange(BacktestJobTopology.JOB_EXCHANGE, true, false);
        DirectExchange deadLetters =
                new DirectExchange(BacktestJobTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(BacktestJobTopology.JOB_QUEUE)
                .deadLetterExchange(BacktestJobTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(BacktestJobTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(BacktestJobTopology.DEAD_LETTER_QUEUE).build();
        admin.declareExchange(jobs);
        admin.declareExchange(deadLetters);
        admin.declareQueue(queue);
        admin.declareQueue(deadLetterQueue);
        admin.declareBinding(BindingBuilder.bind(queue).to(jobs).with(BacktestJobTopology.JOB_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(deadLetterQueue)
                .to(deadLetters)
                .with(BacktestJobTopology.DEAD_LETTER_ROUTING_KEY));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
