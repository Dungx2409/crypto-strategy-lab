package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.RandomStrategyGenerator;
import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStartCommand;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditionEvaluator;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.infrastructure.experiment.adapter.JdbcSearchRunRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.port.StrategyFactory;
import com.cryptolab.strategy.port.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SearchRunIT {

    private static final UUID DATASET_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID COMPLETE_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID CANCEL_ID = UUID.fromString("50000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final MarketDatasetRef DATASET = new MarketDatasetRef(
            "BTCUSDT",
            Timeframe.M5,
            Instant.parse("2026-08-18T00:00:00Z"),
            Instant.parse("2026-08-18T01:00:00Z"),
            "search-it-v1",
            "search-checksum-v1");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    private static JdbcTemplate jdbc;
    private static JdbcSearchRunRepository repository;
    private static RandomStrategyGenerator generator;

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
                java.time.OffsetDateTime.ofInstant(DATASET.from(), ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(DATASET.to(), ZoneOffset.UTC),
                DATASET.datasetVersion(),
                DATASET.checksum(),
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        repository = new JdbcSearchRunRepository(jdbc, new ObjectMapper().findAndRegisterModules());
        generator = new RandomStrategyGenerator(new TestRegistry());
    }

    @Test
    void persistsDeterministicProgressInBoundedBatches() {
        SearchCoordinator coordinator = coordinator(repository);

        SearchRunSummary completed = coordinator.start(command(COMPLETE_ID, 7L, 3));

        assertThat(completed.run().status()).isEqualTo(SearchRunStatus.COMPLETED);
        assertThat(completed.stopReason()).isEqualTo(SearchStopReason.MAX_CANDIDATES);
        assertThat(completed.generatedCandidates()).isEqualTo(7);
        assertThat(completed.persistedCandidates()).isEqualTo(7);
        assertThat(completed.pendingDispatchJobs()).isEqualTo(7);
        assertThat(completed.queuedJobs()).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM candidates WHERE search_run_id = ?", Integer.class, COMPLETE_ID))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM backtest_jobs WHERE search_run_id = ? "
                                + "AND status = 'PENDING_DISPATCH'",
                        Integer.class,
                        COMPLETE_ID))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM experiments WHERE search_run_id = ? "
                                + "AND status = 'CREATED'",
                        Integer.class,
                        COMPLETE_ID))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject(
                        "SELECT random_seed FROM search_runs WHERE id = ?", Long.class, COMPLETE_ID))
                .isEqualTo(9876L);
        assertThat(jdbc.queryForObject(
                        "SELECT search_config_json -> 'strategyVersions' ->> 'TEST' "
                                + "FROM search_runs WHERE id = ?",
                        String.class,
                        COMPLETE_ID))
                .isEqualTo("1.0");
    }

    @Test
    void concurrentCancellationStopsAtTheNextPersistedBatchBoundary() throws Exception {
        BlockingRepository blocking = new BlockingRepository(repository);
        SearchCoordinator coordinator = coordinator(blocking);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> coordinator.start(command(CANCEL_ID, 100L, 1)));
            assertThat(blocking.firstBatchPersisted.await(5, TimeUnit.SECONDS)).isTrue();

            coordinator.recordEvaluation(CANCEL_ID, new BigDecimal("10"));
            coordinator.recordEvaluation(CANCEL_ID, new BigDecimal("9"));
            coordinator.recordEvaluation(CANCEL_ID, new BigDecimal("8"));
            SearchRunSummary feedback = coordinator.details(CANCEL_ID);
            assertThat(feedback.bestScore()).isEqualByComparingTo("10");
            assertThat(feedback.noImprovementIterations()).isEqualTo(2);

            SearchRunSummary accepted = coordinator.cancel(CANCEL_ID);
            assertThat(accepted.run().cancelRequested()).isTrue();
            assertThat(accepted.run().status()).isEqualTo(SearchRunStatus.CANCELLED);
            blocking.releaseFirstBatch.countDown();

            SearchRunSummary cancelled = future.get(5, TimeUnit.SECONDS);
            assertThat(cancelled.run().status()).isEqualTo(SearchRunStatus.CANCELLED);
            assertThat(cancelled.stopReason()).isEqualTo(SearchStopReason.USER_CANCELLED);
            assertThat(cancelled.generatedCandidates()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM candidates WHERE search_run_id = ?", Integer.class, CANCEL_ID))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM backtest_jobs WHERE search_run_id = ? AND status = 'CANCELLED'",
                            Integer.class,
                            CANCEL_ID))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM experiments WHERE search_run_id = ? AND status = 'CANCELLED'",
                            Integer.class,
                            CANCEL_ID))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            """
                            SELECT count(*) FROM outbox_events outbox
                            JOIN backtest_jobs job ON job.outbox_event_id = outbox.event_id
                            WHERE job.search_run_id = ? AND outbox.cancelled_at IS NOT NULL
                            """,
                            Integer.class,
                            CANCEL_ID))
                    .isEqualTo(1);
        }
    }

    private static SearchCoordinator coordinator(SearchRunRepository searchRepository) {
        return new SearchCoordinator(
                generator,
                searchRepository,
                new StopConditionEvaluator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new JobDispatchMetadata("evaluator-v1", "test-commit", "test-build"));
    }

    private static SearchStartCommand command(UUID id, long maxCandidates, int batchSize) {
        List<Object> values = IntStream.rangeClosed(1, 100).boxed().map(value -> (Object) value).toList();
        SearchContext context = new SearchContext(
                id,
                DATASET,
                List.of("TEST"),
                Map.of("TEST", "1.0"),
                new SearchParameterSpace(Map.of("TEST", Map.of("value", values))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                9876L,
                new StopConditions(maxCandidates, null, null),
                batchSize);
        return new SearchStartCommand(
                context,
                new ExecutionConfig(
                        new BigDecimal("10000"),
                        new BigDecimal("0.001"),
                        false,
                        "NEXT_CANDLE_OPEN",
                        "deterministic-next-open-v1"));
    }

    private static final class TestRegistry implements StrategyRegistry {

        @Override
        public Strategy create(StrategyDefinition definition) {
            return null;
        }

        @Override
        public void register(StrategyFactory factory) {}

        @Override
        public Set<String> registeredTypes() {
            return Set.of("TEST");
        }

        @Override
        public List<StrategyPluginDescriptor> availableStrategies() {
            return List.of(new StrategyPluginDescriptor(
                    "TEST", "1.0", Map.of("value", Map.of("default", 1))));
        }
    }

    private static final class BlockingRepository implements SearchRunRepository {

        private final SearchRunRepository delegate;
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final CountDownLatch firstBatchPersisted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstBatch = new CountDownLatch(1);

        private BlockingRepository(SearchRunRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void create(SearchRun run, ExecutionConfig executionConfig) {
            delegate.create(run, executionConfig);
        }

        @Override
        public void transition(
                UUID searchRunId,
                SearchRunStatus expected,
                SearchRunStatus target,
                SearchStopReason stopReason,
                Instant at) {
            delegate.transition(searchRunId, expected, target, stopReason, at);
        }

        @Override
        public int appendCandidatesAndCreateJobs(
                SearchRun run,
                ExecutionConfig executionConfig,
                JobDispatchMetadata dispatchMetadata,
                List<CandidateStrategy> candidates,
                Instant generatedAt) {
            int persisted = delegate.appendCandidatesAndCreateJobs(
                    run, executionConfig, dispatchMetadata, candidates, generatedAt);
            if (first.compareAndSet(true, false)) {
                firstBatchPersisted.countDown();
                try {
                    if (!releaseFirstBatch.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release first batch");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("search test interrupted", exception);
                }
            }
            return persisted;
        }

        @Override
        public boolean cancel(UUID searchRunId, Instant cancelledAt) {
            return delegate.cancel(searchRunId, cancelledAt);
        }

        @Override
        public void recordEvaluation(UUID searchRunId, BigDecimal score) {
            delegate.recordEvaluation(searchRunId, score);
        }

        @Override
        public void fail(UUID searchRunId, String failureCode, String failureMessage, Instant failedAt) {
            delegate.fail(searchRunId, failureCode, failureMessage, failedAt);
        }

        @Override
        public Optional<SearchRunSummary> findSummary(UUID searchRunId) {
            return delegate.findSummary(searchRunId);
        }
    }
}
