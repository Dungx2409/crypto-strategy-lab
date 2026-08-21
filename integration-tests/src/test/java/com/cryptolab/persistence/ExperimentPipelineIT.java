package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetChecksum;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.RerunResult;
import com.cryptolab.infrastructure.experiment.adapter.DefaultCombinationPolicyResolver;
import com.cryptolab.infrastructure.experiment.adapter.JdbcExperimentRepository;
import com.cryptolab.infrastructure.strategy.adapter.SpringStrategyRegistry;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import com.cryptolab.strategy.port.StrategyFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class ExperimentPipelineIT {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID EXPERIMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RERUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SEARCH_RUN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    private static JdbcTemplate jdbc;
    private static JdbcExperimentRepository repository;
    private static ExperimentPipelineService pipeline;

    @BeforeAll
    static void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcExperimentRepository(
                jdbc, new ObjectMapper().findAndRegisterModules());
        var registry = new SpringStrategyRegistry(List.of(new TestSignalStrategyFactory()));
        var engine = new DeterministicBacktestEngine(
                repository,
                repository,
                registry,
                new DefaultCombinationPolicyResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var evaluator = new DefaultExperimentEvaluator();
        pipeline = new ExperimentPipelineService(
                engine,
                evaluator,
                new DefaultRankingService(),
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> RERUN_ID);
    }

    @Test
    void persistsCompletePipelineAndCanReconstructAndRerunProvenance() {
        ExperimentPlan plan = plan();

        ExperimentDetails completed = pipeline.execute(plan);

        assertThat(completed.experiment().status()).isEqualTo(ExperimentStatus.COMPLETED);
        assertThat(completed.signals()).hasSize(6);
        assertThat(completed.trades()).hasSize(1);
        assertThat(completed.metrics()).isNotNull();
        assertThat(completed.metrics().winRatePct()).isEqualByComparingTo("100");
        assertThat(completed.rank()).isEqualTo(1);
        assertCount("experiment_signals", 6);
        assertCount("trades", 1);
        assertCount("evaluation_metrics", 1);
        assertCount("leaderboard_entries", 1);
        assertThat(jdbc.queryForObject(
                        "SELECT win_rate_pct FROM evaluation_metrics WHERE experiment_id = ?",
                        BigDecimal.class,
                        EXPERIMENT_ID))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject(
                        "SELECT win_rate_pct FROM leaderboard_entries WHERE experiment_id = ?",
                        BigDecimal.class,
                        EXPERIMENT_ID))
                .isEqualByComparingTo("100");

        var top = pipeline.leaderboard(SEARCH_RUN_ID, 1).getFirst();
        var topProvenance = pipeline.provenance(top.ranking().experimentId());
        assertThat(top.ranking().rank()).isEqualTo(1);
        assertThat(topProvenance.experimentId()).isEqualTo(EXPERIMENT_ID);
        assertThat(topProvenance.candidateHash()).isEqualTo(plan.candidate().candidateHash());
        assertThat(topProvenance.strategies()).isEqualTo(plan.candidate().strategies());
        assertThat(topProvenance.combinationPolicy()).isEqualTo(plan.candidate().combinationPolicy());
        assertThat(topProvenance.dataset()).isEqualTo(plan.dataset().reference());
        assertThat(topProvenance.executionConfig()).isEqualTo(plan.executionConfig());
        assertThat(topProvenance.generator()).isEqualTo(plan.generator());
        assertThat(topProvenance.evaluatorVersion()).isEqualTo(DefaultExperimentEvaluator.VERSION);
        assertThat(topProvenance.codeCommit()).isEqualTo("abc123");
        assertThat(topProvenance.buildVersion()).isEqualTo("0.1.0-test");
        assertThat(topProvenance.metrics()).isEqualTo(completed.metrics());

        String immutableStrategies = jdbc.queryForObject(
                "SELECT strategy_snapshot_json::text FROM experiments WHERE id = ?",
                String.class,
                EXPERIMENT_ID);
        ExperimentPlan reconstructed = repository.findPlan(EXPERIMENT_ID).orElseThrow();
        assertThat(reconstructed.candidate()).isEqualTo(plan.candidate());
        assertThat(reconstructed.dataset().reference()).isEqualTo(plan.dataset().reference());
        assertThat(reconstructed.executionConfig()).isEqualTo(plan.executionConfig());
        assertThat(reconstructed.generator()).isEqualTo(plan.generator());
        assertThat(reconstructed.codeCommit()).isEqualTo("abc123");

        RerunResult rerun = pipeline.rerun(EXPERIMENT_ID);

        assertThat(rerun.metricsMatch()).isTrue();
        assertThat(rerun.reproducedExperiment().experiment().id()).isEqualTo(RERUN_ID);
        assertThat(rerun.reproducedExperiment().experiment().reproductionOfExperimentId())
                .isEqualTo(EXPERIMENT_ID);
        assertThat(jdbc.queryForObject(
                        "SELECT strategy_snapshot_json::text FROM experiments WHERE id = ?",
                        String.class,
                        EXPERIMENT_ID))
                .isEqualTo(immutableStrategies);
        assertCount("experiments", 2);
        assertCount("evaluation_metrics", 2);
        assertCount("leaderboard_entries", 2);
        assertThat(pipeline.leaderboard(SEARCH_RUN_ID, 50))
                .extracting(entry -> entry.ranking().rank())
                .containsExactly(1, 2);

        assertThatThrownBy(() -> repository.transition(
                        EXPERIMENT_ID,
                        ExperimentStatus.COMPLETED,
                        ExperimentStatus.RUNNING,
                        NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED -> RUNNING");
    }

    private static ExperimentPlan plan() {
        List<Candle> candles = List.of(
                candle(0, "100", "105"),
                candle(1, "110", "115"),
                candle(2, "120", "125"));
        MarketDatasetRef reference = new MarketDatasetRef(
                "BTCUSDT",
                Timeframe.M5,
                candles.getFirst().openTime(),
                candles.getLast().openTime().plus(Timeframe.M5.duration()),
                "integration-v1",
                MarketDatasetChecksum.calculate(candles));
        MarketDataset dataset = new MarketDataset(DATASET_ID, reference, candles);
        List<StrategyDefinition> strategies = List.of(new StrategyDefinition("TEST", "1.0", Map.of()));
        CombinationPolicyDefinition policy = new CombinationPolicyDefinition(
                "MAJORITY", "1.0", Map.of(), BigDecimal.ZERO);
        CandidateStrategy candidate = new CandidateStrategy(
                CANDIDATE_ID,
                strategies,
                policy,
                CandidateCanonicalizer.hash(strategies, policy));
        return new ExperimentPlan(
                EXPERIMENT_ID,
                SEARCH_RUN_ID,
                candidate,
                dataset,
                new ExecutionConfig(
                        new BigDecimal("10000"),
                        new BigDecimal("0.001"),
                        false,
                        DeterministicBacktestEngine.FILL_POLICY,
                        DeterministicBacktestEngine.VERSION),
                new GeneratorSnapshot(
                        "manual",
                        "1.0",
                        Map.of("mode", "single", "selection", Map.of("strategy", "TEST")),
                        null),
                DefaultExperimentEvaluator.VERSION,
                "abc123",
                "0.1.0-test",
                null,
                NOW);
    }

    private static Candle candle(int index, String open, String close) {
        BigDecimal openValue = new BigDecimal(open);
        BigDecimal closeValue = new BigDecimal(close);
        return new Candle(
                "BTCUSDT",
                Timeframe.M5,
                Instant.parse("2026-08-18T00:00:00Z").plusSeconds(index * 300L),
                openValue,
                openValue.max(closeValue).add(BigDecimal.ONE),
                openValue.min(closeValue).subtract(BigDecimal.ONE),
                closeValue,
                BigDecimal.TEN);
    }

    private static void assertCount(String table, int expected) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                .isEqualTo(expected);
    }

    private static final class TestSignalStrategyFactory implements StrategyFactory {

        @Override
        public String type() {
            return "TEST";
        }

        @Override
        public String version() {
            return "1.0";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return Map.of();
        }

        @Override
        public Strategy create(StrategyDefinition definition) {
            return new Strategy() {
                @Override
                public StrategyDescriptor descriptor() {
                    return new StrategyDescriptor("TEST", "1.0", Map.of());
                }

                @Override
                public Signal analyze(StrategyContext context) {
                    SignalType type = context.candles().size() == 1
                            ? SignalType.BUY
                            : context.candles().size() == 2 ? SignalType.SELL : SignalType.HOLD;
                    BigDecimal strength = type == SignalType.BUY
                            ? BigDecimal.ONE
                            : type == SignalType.SELL ? BigDecimal.ONE.negate() : BigDecimal.ZERO;
                    return new Signal(type, strength, context.evaluatedAt(), "integration signal");
                }
            };
        }
    }
}
