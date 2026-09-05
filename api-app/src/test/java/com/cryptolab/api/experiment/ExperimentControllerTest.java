package com.cryptolab.api.experiment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.application.ExperimentPlanFactory;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.Experiment;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.SortDirection;
import com.cryptolab.experiment.port.CandidateProvider;
import com.cryptolab.experiment.port.ExperimentRepository;
import com.cryptolab.experiment.port.MarketDatasetProvider;
import com.cryptolab.infrastructure.experiment.adapter.DefaultCombinationPolicyResolver;
import com.cryptolab.infrastructure.strategy.adapter.SpringStrategyRegistry;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import com.cryptolab.strategy.port.StrategyFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExperimentControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID DATASET_ID = uuid(1);
    private static final UUID CANDIDATE_ID = uuid(2);
    private static final UUID EXPERIMENT_ID = uuid(3);
    private static final UUID SEARCH_RUN_ID = uuid(4);
    private static final UUID RERUN_ID = uuid(5);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryExperimentRepository repository = new InMemoryExperimentRepository();
        var registry = new SpringStrategyRegistry(List.of(new TestSignalStrategyFactory()));
        var evaluator = new DefaultExperimentEvaluator();
        var backtest = new DeterministicBacktestEngine(
                repository,
                repository,
                registry,
                new DefaultCombinationPolicyResolver(),
                clock);
        ExperimentPipelineService pipeline = new ExperimentPipelineService(
                backtest,
                evaluator,
                new DefaultRankingService(),
                repository,
                clock,
                () -> RERUN_ID);
        ArrayDeque<UUID> ids = new ArrayDeque<>(
                List.of(DATASET_ID, CANDIDATE_ID, EXPERIMENT_ID, SEARCH_RUN_ID));
        ExperimentPlanFactory plans = new ExperimentPlanFactory(
                evaluator.version(), "test-commit", "test-build", clock, ids::removeFirst);
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ExperimentController(plans, pipeline),
                        new LeaderboardController(pipeline))
                .setControllerAdvice(new ExperimentExceptionHandler(clock))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void runsCandidateAndExposesDetailsProvenanceLeaderboardAndRerun() throws Exception {
        MockHttpSession session = authenticatedSession();
        mockMvc.perform(post("/api/v1/experiments")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/experiments/" + EXPERIMENT_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.candidateHash").isNotEmpty())
                .andExpect(jsonPath("$.dataset.timeframe").value("5m"))
                .andExpect(jsonPath("$.executionConfig.fillPolicy").value("NEXT_CANDLE_OPEN"))
                .andExpect(jsonPath("$.generator.type").value("manual"))
                .andExpect(jsonPath("$.signals.length()").value(6))
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].direction").value("LONG"))
                .andExpect(jsonPath("$.trades[0].exitReason").value("SIGNAL"))
                .andExpect(jsonPath("$.metrics.totalTrades").value(1))
                .andExpect(jsonPath("$.metrics.winRatePct").value(100));

        mockMvc.perform(get("/api/v1/experiments/{id}", EXPERIMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeCommit").value("test-commit"))
                .andExpect(jsonPath("$.dataset.checksum").isNotEmpty());

        mockMvc.perform(get("/api/v1/experiments/{id}/provenance", EXPERIMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(CANDIDATE_ID.toString()))
                .andExpect(jsonPath("$.strategies[0].type").value("TEST"))
                .andExpect(jsonPath("$.dataset.timeframe").value("5m"))
                .andExpect(jsonPath("$.dataset.datasetVersion").value("api-test-v1"))
                .andExpect(jsonPath("$.executionConfig.feeRate").value(0.001))
                .andExpect(jsonPath("$.evaluatorVersion").value(DefaultExperimentEvaluator.VERSION))
                .andExpect(jsonPath("$.metrics.winRatePct").value(100));

        mockMvc.perform(get("/api/v1/experiments/{id}/candles", EXPERIMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.candles.length()").value(3))
                .andExpect(jsonPath("$.candles[0].openTime").value("2026-08-18T00:00:00Z"))
                .andExpect(jsonPath("$.candles[2].close").value(125));

        mockMvc.perform(get("/api/v1/leaderboard")
                        .param("searchRunId", SEARCH_RUN_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].experimentId").value(EXPERIMENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].strategySummary").value("TEST"))
                .andExpect(jsonPath("$.items[0].winRatePct").value(100));

        mockMvc.perform(post("/api/v1/experiments/{id}/rerun", EXPERIMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsMatch").value(true))
                .andExpect(jsonPath("$.experiment.experimentId").value(RERUN_ID.toString()))
                .andExpect(jsonPath("$.experiment.reproductionOfExperimentId")
                        .value(EXPERIMENT_ID.toString()));
    }

    @Test
    void mapsMalformedAndMissingExperimentsToStableErrors() throws Exception {
        mockMvc.perform(post("/api/v1/experiments")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"BTCUSDT\",\"timeframe\":\"2m\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPERIMENT_REQUEST"));

        mockMvc.perform(get("/api/v1/experiments/{id}", uuid(99)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_NOT_FOUND"));
    }

    private static String requestJson() {
        return """
                {
                  "symbol": "BTCUSDT",
                  "timeframe": "5m",
                  "datasetVersion": "api-test-v1",
                  "candles": [
                    {"openTime":"2026-08-18T00:00:00Z","open":100,"high":106,"low":99,"close":105,"volume":10},
                    {"openTime":"2026-08-18T00:05:00Z","open":110,"high":116,"low":109,"close":115,"volume":10},
                    {"openTime":"2026-08-18T00:10:00Z","open":120,"high":126,"low":119,"close":125,"volume":10}
                  ],
                  "strategies": [{"type":"TEST","version":"1.0","parameters":{}}],
                  "combinationPolicy": {"type":"MAJORITY","version":"1.0","weights":{},"threshold":0}
                }
                """;
    }

    private static MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedAccount.class.getName(),
                new AuthenticatedAccount(uuid(42), "student"));
        return session;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }

    private static final class InMemoryExperimentRepository
            implements ExperimentRepository, CandidateProvider, MarketDatasetProvider {

        private final Map<UUID, State> states = new HashMap<>();
        private final Map<UUID, CandidateStrategy> candidates = new HashMap<>();
        private final Map<String, MarketDataset> datasets = new HashMap<>();
        private final Map<UUID, List<Ranking>> rankings = new HashMap<>();

        @Override
        public void create(ExperimentPlan plan) {
            states.put(plan.experimentId(), new State(plan));
            candidates.put(plan.candidate().candidateId(), plan.candidate());
            datasets.put(plan.dataset().reference().checksum(), plan.dataset());
        }

        @Override
        public void transition(
                UUID experimentId,
                ExperimentStatus expected,
                ExperimentStatus target,
                Instant at) {
            State state = states.get(experimentId);
            state.status = target;
            state.startedAt = at;
        }

        @Override
        public void complete(UUID experimentId, BacktestResult result, Evaluation evaluation, Instant completedAt) {
            State state = states.get(experimentId);
            state.status = ExperimentStatus.COMPLETED;
            state.completedAt = completedAt;
            state.result = result;
            state.evaluation = evaluation;
        }

        @Override
        public void fail(UUID experimentId, String failureCode, String failureMessage, Instant failedAt) {
            states.get(experimentId).status = ExperimentStatus.FAILED;
        }

        @Override
        public List<Evaluation> findCompletedEvaluations(UUID searchRunId) {
            return states.values().stream()
                    .filter(state -> state.plan.searchRunId().equals(searchRunId))
                    .filter(state -> state.evaluation != null)
                    .map(state -> state.evaluation)
                    .toList();
        }

        @Override
        public void replaceLeaderboard(UUID searchRunId, List<Ranking> values, Instant updatedAt) {
            rankings.put(searchRunId, List.copyOf(values));
        }

        @Override
        public List<LeaderboardEntry> findLeaderboard(
                UUID searchRunId, int limit, LeaderboardSort sort, SortDirection direction) {
            return rankings.getOrDefault(searchRunId, List.of()).stream()
                    .sorted(leaderboardComparator(sort, direction))
                    .limit(limit)
                    .map(ranking -> {
                        CandidateStrategy candidate = states.get(ranking.experimentId()).plan.candidate();
                        String summary = candidate.strategies().stream()
                                .map(StrategyDefinition::type)
                                .reduce((left, right) -> left + "+" + right)
                                .orElseThrow();
                        return new LeaderboardEntry(searchRunId, ranking, summary);
                    })
                    .toList();
        }

        @Override
        public List<LeaderboardEntry> findAllTimeLeaderboard(
                int limit, LeaderboardSort sort, SortDirection direction) {
            return List.of();
        }

        private static java.util.Comparator<Ranking> leaderboardComparator(
                LeaderboardSort sort, SortDirection direction) {
            java.util.Comparator<Ranking> comparator = switch (sort) {
                case RANK -> java.util.Comparator.comparingInt(Ranking::rank);
                case SCORE -> java.util.Comparator.comparing(ranking -> ranking.metrics().score());
                case RETURN -> java.util.Comparator.comparing(ranking -> ranking.metrics().totalReturnPct());
                case WIN_RATE -> java.util.Comparator.comparing(ranking -> ranking.metrics().winRatePct());
                case MAX_DRAWDOWN -> java.util.Comparator.comparing(ranking -> ranking.metrics().maxDrawdownPct());
                case TRADES -> java.util.Comparator.comparingInt(ranking -> ranking.metrics().totalTrades());
            };
            if (direction == SortDirection.DESC) {
                comparator = comparator.reversed();
            }
            return comparator.thenComparingInt(Ranking::rank);
        }

        @Override
        public Optional<ExperimentDetails> findDetails(UUID experimentId) {
            State state = states.get(experimentId);
            if (state == null) {
                return Optional.empty();
            }
            ExperimentPlan plan = state.plan;
            Integer rank = rankings.getOrDefault(plan.searchRunId(), List.of()).stream()
                    .filter(value -> value.experimentId().equals(experimentId))
                    .map(Ranking::rank)
                    .findFirst()
                    .orElse(null);
            Experiment experiment = new Experiment(
                    plan.experimentId(),
                    plan.candidate().candidateId(),
                    plan.searchRunId(),
                    state.status,
                    plan.dataset().reference(),
                    plan.executionConfig(),
                    plan.candidate().strategies(),
                    plan.candidate().combinationPolicy(),
                    plan.generator().type(),
                    plan.generator().version(),
                    plan.generator().randomSeed(),
                    plan.evaluatorVersion(),
                    plan.codeCommit(),
                    plan.buildVersion(),
                    plan.reproductionOfExperimentId(),
                    state.startedAt,
                    state.completedAt,
                    null,
                    null,
                    2);
            return Optional.of(new ExperimentDetails(
                    experiment,
                    plan.candidate(),
                    plan.generator(),
                    state.result == null ? List.of() : state.result.signals(),
                    state.result == null ? List.of() : state.result.trades(),
                    state.evaluation == null ? null : state.evaluation.metrics(),
                    rank));
        }

        @Override
        public Optional<ExperimentPlan> findPlan(UUID experimentId) {
            State state = states.get(experimentId);
            return state == null ? Optional.empty() : Optional.of(state.plan);
        }

        @Override
        public CandidateStrategy getCandidate(UUID candidateId) {
            return candidates.get(candidateId);
        }

        @Override
        public MarketDataset getDataset(MarketDatasetRef reference) {
            return datasets.get(reference.checksum());
        }

        private static final class State {
            private final ExperimentPlan plan;
            private ExperimentStatus status = ExperimentStatus.CREATED;
            private Instant startedAt;
            private Instant completedAt;
            private BacktestResult result;
            private Evaluation evaluation;

            private State(ExperimentPlan plan) {
                this.plan = plan;
            }
        }
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
                    return new Signal(type, strength, context.evaluatedAt(), "API test signal");
                }
            };
        }
    }
}
