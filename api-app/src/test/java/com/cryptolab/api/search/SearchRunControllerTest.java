package com.cryptolab.api.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.account.domain.AccountRole;
import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditionEvaluator;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.experiment.port.StrategyGenerator;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SearchRunControllerTest {

    private static final UUID SEARCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    private MockMvc mockMvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        InMemoryRepository repository = new InMemoryRepository();
        AtomicInteger sequence = new AtomicInteger();
        StrategyGenerator generator = new StrategyGenerator() {
            @Override
            public String type() {
                return "random";
            }

            @Override
            public String version() {
                return "1.0";
            }

            @Override
            public Stream<CandidateStrategy> generate(SearchContext context) {
                return Stream.generate(() -> candidate(sequence.getAndIncrement()));
            }
        };
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SearchCoordinator coordinator = new SearchCoordinator(
                generator,
                repository,
                new StopConditionEvaluator(),
                clock,
                new JobDispatchMetadata("evaluator-v1", "test-commit", "test-build"));
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SearchRunController(coordinator, () -> SEARCH_ID, Runnable::run))
                .setControllerAdvice(new SearchRunExceptionHandler(clock))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
        session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedAccount.class.getName(),
                new AuthenticatedAccount(UUID.randomUUID(), "student", AccountRole.USER));
    }

    @Test
    void startsBoundedSearchAndExposesProgressAndCancellationEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/search-runs").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/search-runs/" + SEARCH_ID))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.generatorType").value("random"))
                .andExpect(jsonPath("$.randomSeed").value(12345))
                .andExpect(jsonPath("$.generatedCandidates").value(0))
                .andExpect(jsonPath("$.persistedCandidates").value(0));

        mockMvc.perform(get("/api/v1/search-runs/{id}", SEARCH_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchSize").value(2))
                .andExpect(jsonPath("$.status").value("EVALUATING"))
                .andExpect(jsonPath("$.generatedCandidates").value(5))
                .andExpect(jsonPath("$.persistedCandidates").value(5))
                .andExpect(jsonPath("$.pendingDispatchJobs").value(5))
                .andExpect(jsonPath("$.queuedJobs").value(0))
                .andExpect(jsonPath("$.stopReason").value("MAX_CANDIDATES"));

        mockMvc.perform(post("/api/v1/search-runs/{id}/cancel", SEARCH_ID).session(session))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EVALUATING"));
    }

    @Test
    void rejectsMissingSeedAndMapsMissingRun() throws Exception {
        mockMvc.perform(post("/api/v1/search-runs").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson().replace("\"randomSeed\": 12345,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));

        mockMvc.perform(get("/api/v1/search-runs/{id}", UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEARCH_RUN_NOT_FOUND"));
    }

    @Test
    void exposesRuntimeGeneratorSelectionCapabilities() throws Exception {
        mockMvc.perform(get("/api/v1/search-runs/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultGenerator").value("random"))
                .andExpect(jsonPath("$.availableGenerators[0]").value("random"))
                .andExpect(jsonPath("$.engineVersion").value("deterministic-next-open-v5"))
                .andExpect(jsonPath("$.fillPolicy").value("NEXT_CANDLE_OPEN"));
    }

    @Test
    void identifiesTheProductionConstructorForSpringInjection() throws Exception {
        assertThat(SearchRunController.class
                        .getConstructor(SearchCoordinator.class, org.springframework.core.task.TaskExecutor.class)
                        .isAnnotationPresent(Autowired.class))
                .isTrue();
    }

    private static String requestJson() {
        return """
                {
                  "symbol":"BTCUSDT",
                  "timeframe":"5m",
                  "from":"2026-08-18T00:00:00Z",
                  "to":"2026-08-18T01:00:00Z",
                  "datasetVersion":"search-api-v1",
                  "datasetChecksum":"checksum-1",
                  "strategyTypes":["MA"],
                  "strategyVersions":{"MA":"1.0"},
                  "parameterSpace":{"MA":{"fastPeriod":[10,20],"slowPeriod":[50,100]}},
                  "combinationPolicy":{"type":"MAJORITY","version":"1.0","weights":{},"threshold":0},
                  "randomSeed": 12345,
                  "stopConditions":{"maxCandidates":5},
                  "batchSize":2
                }
                """;
    }

    private static CandidateStrategy candidate(int index) {
        List<StrategyDefinition> strategies = List.of(
                new StrategyDefinition("MA", "1.0", Map.of("fastPeriod", index + 1, "slowPeriod", 100)));
        var policy = new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO);
        return new CandidateStrategy(
                new UUID(0, index + 1L), strategies, policy, CandidateCanonicalizer.hash(strategies, policy));
    }

    private static final class InMemoryRepository implements SearchRunRepository {

        private SearchRun run;
        private long generated;
        private long persisted;
        private SearchStopReason reason;

        @Override
        public void create(SearchRun run, ExecutionConfig executionConfig) {
            this.run = run;
        }

        @Override
        public void transition(
                UUID searchRunId,
                SearchRunStatus expected,
                SearchRunStatus target,
                SearchStopReason stopReason,
                Instant at) {
            reason = stopReason;
            run = new SearchRun(
                    run.id(), target, run.context(), run.generatorType(), run.generatorVersion(), run.createdAt(),
                    target == SearchRunStatus.RUNNING ? at : run.startedAt(),
                    target == SearchRunStatus.COMPLETED || target == SearchRunStatus.CANCELLED ? at : null,
                    run.cancelRequested(),
                    run.ownerAccountId(),
                    run.runKind());
        }

        @Override
        public void finishGeneration(
                UUID searchRunId, SearchStopReason stopReason, Instant at) {
            transition(
                    searchRunId,
                    SearchRunStatus.RUNNING,
                    SearchRunStatus.EVALUATING,
                    stopReason,
                    at);
        }

        @Override
        public int appendCandidatesAndCreateJobs(
                SearchRun run,
                ExecutionConfig executionConfig,
                JobDispatchMetadata dispatchMetadata,
                List<CandidateStrategy> candidates,
                Instant generatedAt) {
            generated += candidates.size();
            persisted += candidates.size();
            return candidates.size();
        }

        @Override
        public boolean cancel(UUID searchRunId, Instant cancelledAt) {
            return false;
        }

        @Override
        public void recordEvaluation(UUID searchRunId, BigDecimal score) {}

        @Override
        public void fail(UUID searchRunId, String failureCode, String failureMessage, Instant failedAt) {}

        @Override
        public Optional<SearchRunSummary> findSummary(UUID searchRunId) {
            if (run == null || !run.id().equals(searchRunId)) {
                return Optional.empty();
            }
            return Optional.of(new SearchRunSummary(
                    run, generated, persisted, persisted, 0, 0, 0, 0, 0,
                    null, 0, reason, null, null));
        }
    }
}
