package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.SortDirection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class JdbcLeaderboardQueryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcLeaderboardQueryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    List<LeaderboardEntry> findLeaderboard(
            UUID searchRunId, int limit, LeaderboardSort sort, SortDirection direction) {
        String orderBy = leaderboardOrderBy(sort, direction);
        return jdbcTemplate.query(
                """
                SELECT l.search_run_id, l.rank, l.experiment_id, l.score, l.return_pct,
                       l.max_drawdown_pct, l.total_trades, l.win_rate_pct, c.candidate_spec_json
                FROM leaderboard_entries l
                JOIN experiments e ON e.id = l.experiment_id
                JOIN candidates c ON c.id = e.candidate_id
                WHERE l.search_run_id = ?
                ORDER BY %s, l.rank
                LIMIT ?
                """
                        .formatted(orderBy),
                (resultSet, rowNumber) -> {
                    CandidateStrategy candidate = read(
                            resultSet.getString("candidate_spec_json"), CandidateStrategy.class);
                    EvaluationMetrics values = new EvaluationMetrics(
                            resultSet.getBigDecimal("return_pct"),
                            resultSet.getBigDecimal("max_drawdown_pct"),
                            resultSet.getInt("total_trades"),
                            resultSet.getBigDecimal("win_rate_pct"),
                            resultSet.getBigDecimal("score"));
                    Ranking ranking = new Ranking(
                            resultSet.getInt("rank"),
                            resultSet.getObject("experiment_id", UUID.class),
                            values);
                    return new LeaderboardEntry(
                            resultSet.getObject("search_run_id", UUID.class),
                            ranking,
                            strategySummary(candidate));
                },
                searchRunId,
                limit);
    }

    List<LeaderboardEntry> findAllTimeLeaderboard(int limit, LeaderboardSort sort, SortDirection direction) {
        String orderBy = allTimeLeaderboardOrderBy(sort, direction);
        return jdbcTemplate.query(
                """
                SELECT e.search_run_id, e.id AS experiment_id, m.score,
                       m.total_return_pct AS return_pct, m.max_drawdown_pct,
                       m.total_trades, m.win_rate_pct, c.candidate_spec_json
                FROM experiments e
                JOIN evaluation_metrics m ON m.experiment_id = e.id
                JOIN candidates c ON c.id = e.candidate_id
                WHERE e.status = 'COMPLETED'
                ORDER BY %s, e.completed_at DESC, e.id
                LIMIT ?
                """
                        .formatted(orderBy),
                (resultSet, rowNumber) -> {
                    CandidateStrategy candidate = read(
                            resultSet.getString("candidate_spec_json"), CandidateStrategy.class);
                    EvaluationMetrics values = new EvaluationMetrics(
                            resultSet.getBigDecimal("return_pct"),
                            resultSet.getBigDecimal("max_drawdown_pct"),
                            resultSet.getInt("total_trades"),
                            resultSet.getBigDecimal("win_rate_pct"),
                            resultSet.getBigDecimal("score"));
                    Ranking ranking = new Ranking(
                            rowNumber + 1,
                            resultSet.getObject("experiment_id", UUID.class),
                            values);
                    return new LeaderboardEntry(
                            resultSet.getObject("search_run_id", UUID.class),
                            ranking,
                            strategySummary(candidate));
                },
                limit);
    }

    private static String leaderboardOrderBy(LeaderboardSort sort, SortDirection direction) {
        String column = switch (sort) {
            case RANK -> "l.rank";
            case SCORE -> "l.score";
            case RETURN -> "l.return_pct";
            case WIN_RATE -> "l.win_rate_pct";
            case MAX_DRAWDOWN -> "l.max_drawdown_pct";
            case TRADES -> "l.total_trades";
        };
        String sqlDirection = direction == SortDirection.ASC ? "ASC" : "DESC";
        return column + " " + sqlDirection;
    }

    private static String allTimeLeaderboardOrderBy(LeaderboardSort sort, SortDirection direction) {
        String column = switch (sort) {
            case RANK -> "m.score";
            case SCORE -> "m.score";
            case RETURN -> "m.total_return_pct";
            case WIN_RATE -> "m.win_rate_pct";
            case MAX_DRAWDOWN -> "m.max_drawdown_pct";
            case TRADES -> "m.total_trades";
        };
        String sqlDirection = direction == SortDirection.ASC ? "ASC" : "DESC";
        return column + " " + sqlDirection;
    }

    private static String strategySummary(CandidateStrategy candidate) {
        return candidate.strategies().stream()
                .map(strategy -> strategy.displayLabel() == null ? strategy.type() : strategy.displayLabel())
                .reduce((left, right) -> left + "+" + right)
                .orElseThrow();
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot read " + type.getSimpleName() + " JSON", error);
        }
    }
}
