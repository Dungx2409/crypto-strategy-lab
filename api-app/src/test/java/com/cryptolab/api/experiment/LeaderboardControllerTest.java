package com.cryptolab.api.experiment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.SortDirection;
import com.cryptolab.experiment.port.ExperimentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LeaderboardControllerTest {

    private static final UUID SEARCH_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID HIGH_TRADES = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID LOW_TRADES = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        InMemoryLeaderboardRepository repository = new InMemoryLeaderboardRepository();
        repository.replaceLeaderboard(
                SEARCH_RUN_ID,
                List.of(
                        ranking(1, HIGH_TRADES, "12", "2", 8, "80", "12"),
                        ranking(2, LOW_TRADES, "4", "1", 3, "40", "4")),
                clock.instant());
        ExperimentPipelineService pipeline = new ExperimentPipelineService(
                command -> {
                    throw new UnsupportedOperationException();
                },
                new DefaultExperimentEvaluator(),
                new DefaultRankingService(),
                repository,
                clock,
                () -> UUID.randomUUID());
        mockMvc = MockMvcBuilders.standaloneSetup(new LeaderboardController(pipeline))
                .setControllerAdvice(new ExperimentExceptionHandler(clock))
                .build();
    }

    @Test
    void defaultsToScoreDescendingWhenSortParametersAreOmitted() throws Exception {
        mockMvc.perform(get("/api/v1/leaderboard").param("searchRunId", SEARCH_RUN_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].experimentId").value(HIGH_TRADES.toString()))
                .andExpect(jsonPath("$.items[0].score").value(12))
                .andExpect(jsonPath("$.items[1].experimentId").value(LOW_TRADES.toString()));
    }

    @Test
    void sortsByTradeCountAscendingWhenRequestedByTheDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/leaderboard")
                        .param("searchRunId", SEARCH_RUN_ID.toString())
                        .param("limit", "10")
                        .param("sort", "TRADES")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].experimentId").value(LOW_TRADES.toString()))
                .andExpect(jsonPath("$.items[0].totalTrades").value(3))
                .andExpect(jsonPath("$.items[1].experimentId").value(HIGH_TRADES.toString()))
                .andExpect(jsonPath("$.items[1].totalTrades").value(8));
    }

    @Test
    void rejectsUnknownSortValuesWithAStableApiError() throws Exception {
        mockMvc.perform(get("/api/v1/leaderboard")
                        .param("searchRunId", SEARCH_RUN_ID.toString())
                        .param("sort", "profit_factor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPERIMENT_REQUEST"));
    }

    private static Ranking ranking(
            int rank,
            UUID experimentId,
            String totalReturn,
            String drawdown,
            int trades,
            String winRate,
            String score) {
        return new Ranking(
                rank,
                experimentId,
                new EvaluationMetrics(
                        new BigDecimal(totalReturn),
                        new BigDecimal(drawdown),
                        trades,
                        new BigDecimal(winRate),
                        new BigDecimal(score)));
    }

    private static final class InMemoryLeaderboardRepository implements ExperimentRepository {

        private final Map<UUID, List<Ranking>> rankings = new HashMap<>();

        @Override
        public void create(ExperimentPlan plan) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void transition(
                UUID experimentId,
                com.cryptolab.experiment.domain.ExperimentStatus expected,
                com.cryptolab.experiment.domain.ExperimentStatus target,
                Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(
                UUID experimentId, BacktestResult result, Evaluation evaluation, Instant completedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fail(UUID experimentId, String failureCode, String failureMessage, Instant failedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Evaluation> findCompletedEvaluations(UUID searchRunId) {
            return List.of();
        }

        @Override
        public void replaceLeaderboard(UUID searchRunId, List<Ranking> values, Instant updatedAt) {
            rankings.put(searchRunId, List.copyOf(values));
        }

        @Override
        public List<LeaderboardEntry> findLeaderboard(
                UUID searchRunId, int limit, LeaderboardSort sort, SortDirection direction) {
            Comparator<Ranking> comparator = switch (sort) {
                case RANK -> Comparator.comparingInt(Ranking::rank);
                case SCORE -> Comparator.comparing(ranking -> ranking.metrics().score());
                case RETURN -> Comparator.comparing(ranking -> ranking.metrics().totalReturnPct());
                case WIN_RATE -> Comparator.comparing(ranking -> ranking.metrics().winRatePct());
                case MAX_DRAWDOWN -> Comparator.comparing(ranking -> ranking.metrics().maxDrawdownPct());
                case TRADES -> Comparator.comparingInt(ranking -> ranking.metrics().totalTrades());
            };
            if (direction == SortDirection.DESC) {
                comparator = comparator.reversed();
            }
            return rankings.getOrDefault(searchRunId, List.of()).stream()
                    .sorted(comparator.thenComparingInt(Ranking::rank))
                    .limit(limit)
                    .map(ranking -> new LeaderboardEntry(searchRunId, ranking, "TEST"))
                    .toList();
        }

        @Override
        public Optional<ExperimentDetails> findDetails(UUID experimentId) {
            return Optional.empty();
        }

        @Override
        public Optional<ExperimentPlan> findPlan(UUID experimentId) {
            return Optional.empty();
        }
    }
}
