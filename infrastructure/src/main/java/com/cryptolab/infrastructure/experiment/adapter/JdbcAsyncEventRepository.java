package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.experiment.port.AsyncEvaluationRepository;
import com.cryptolab.experiment.port.AsyncRankingRepository;
import com.cryptolab.infrastructure.experiment.messaging.DomainEventTopology;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAsyncEventRepository implements AsyncEvaluationRepository, AsyncRankingRepository {

    static final String EVALUATION_CONSUMER = "async-evaluation";
    static final String RANKING_CONSUMER = "async-ranking";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAsyncEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public boolean processOnce(
            DomainEventEnvelope<BacktestCompletedEvent> sourceEvent,
            Evaluation evaluation,
            DomainEventEnvelope<StrategyEvaluatedEvent> resultEvent,
            Instant processedAt) {
        if (!markProcessed(EVALUATION_CONSUMER, sourceEvent.eventId(), processedAt)) {
            return false;
        }
        verifyPersistedCompletion(sourceEvent.payload(), evaluation);
        recordSearchProgress(sourceEvent.payload().searchRunId(), evaluation.metrics().score());
        appendOutbox(resultEvent, DomainEventTopology.STRATEGY_EVALUATED_ROUTING_KEY);
        return true;
    }

    @Override
    public List<Evaluation> findCompletedEvaluations(UUID searchRunId) {
        return jdbcTemplate.query(
                """
                SELECT e.id, e.evaluator_version, e.completed_at,
                       m.total_return_pct, m.max_drawdown_pct, m.total_trades,
                       m.win_rate_pct, m.score
                FROM experiments e
                JOIN evaluation_metrics m ON m.experiment_id = e.id
                WHERE e.search_run_id = ? AND e.status = 'COMPLETED'
                """,
                (resultSet, rowNumber) -> new Evaluation(
                        resultSet.getObject("id", UUID.class),
                        metrics(resultSet),
                        resultSet.getString("evaluator_version"),
                        resultSet.getObject("completed_at", OffsetDateTime.class).toInstant()),
                searchRunId);
    }

    @Override
    @Transactional
    public boolean replaceLeaderboardOnce(
            DomainEventEnvelope<StrategyEvaluatedEvent> sourceEvent,
            List<Ranking> rankings,
            DomainEventEnvelope<LeaderboardUpdatedEvent> resultEvent,
            Instant processedAt) {
        if (!markProcessed(RANKING_CONSUMER, sourceEvent.eventId(), processedAt)) {
            return false;
        }
        UUID searchRunId = sourceEvent.payload().searchRunId();
        List<Ranking> current = currentRankings(searchRunId);
        if (!sameRankings(current, rankings)) {
            replaceLeaderboard(searchRunId, rankings, processedAt);
            appendOutbox(resultEvent, DomainEventTopology.LEADERBOARD_UPDATED_ROUTING_KEY);
        }
        return true;
    }

    private boolean markProcessed(String consumer, UUID eventId, Instant processedAt) {
        return jdbcTemplate.update(
                        """
                        INSERT INTO processed_events (consumer_name, event_id, processed_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (consumer_name, event_id) DO NOTHING
                        """,
                        consumer,
                        eventId,
                        timestamp(processedAt))
                == 1;
    }

    private void verifyPersistedCompletion(BacktestCompletedEvent event, Evaluation evaluation) {
        List<PersistedCompletion> rows = jdbcTemplate.query(
                """
                SELECT e.search_run_id, e.status, e.evaluator_version,
                       m.total_return_pct, m.max_drawdown_pct, m.total_trades,
                       m.win_rate_pct, m.score
                FROM experiments e
                JOIN evaluation_metrics m ON m.experiment_id = e.id
                WHERE e.id = ?
                """,
                (resultSet, rowNumber) -> new PersistedCompletion(
                        resultSet.getObject("search_run_id", UUID.class),
                        resultSet.getString("status"),
                        resultSet.getString("evaluator_version"),
                        metrics(resultSet)),
                event.experimentId());
        if (rows.size() != 1) {
            throw new IllegalStateException("completed experiment metrics not found: " + event.experimentId());
        }
        PersistedCompletion persisted = rows.getFirst();
        if (!"COMPLETED".equals(persisted.status())
                || !event.searchRunId().equals(persisted.searchRunId())
                || !event.evaluatorVersion().equals(persisted.evaluatorVersion())
                || !sameMetrics(event.metrics(), persisted.metrics())
                || !evaluation.experimentId().equals(event.experimentId())) {
            throw new IllegalStateException("backtest completion does not match persisted provenance");
        }
    }

    private void recordSearchProgress(UUID searchRunId, BigDecimal score) {
        int updated = jdbcTemplate.update(
                """
                UPDATE search_runs
                SET no_improvement_iterations = CASE
                        WHEN best_score IS NULL OR ? > best_score THEN 0
                        ELSE no_improvement_iterations + 1
                    END,
                    best_score = CASE
                        WHEN best_score IS NULL OR ? > best_score THEN ?
                        ELSE best_score
                    END
                WHERE id = ?
                """,
                score,
                score,
                score,
                searchRunId);
        if (updated != 1) {
            throw new IllegalStateException("search run not found: " + searchRunId);
        }
    }

    private List<Ranking> currentRankings(UUID searchRunId) {
        return jdbcTemplate.query(
                """
                SELECT rank, experiment_id, score, return_pct, max_drawdown_pct,
                       total_trades, win_rate_pct
                FROM leaderboard_entries
                WHERE search_run_id = ?
                ORDER BY rank
                """,
                (resultSet, rowNumber) -> new Ranking(
                        resultSet.getInt("rank"),
                        resultSet.getObject("experiment_id", UUID.class),
                        new EvaluationMetrics(
                                resultSet.getBigDecimal("return_pct"),
                                resultSet.getBigDecimal("max_drawdown_pct"),
                                resultSet.getInt("total_trades"),
                                resultSet.getBigDecimal("win_rate_pct"),
                                resultSet.getBigDecimal("score"))),
                searchRunId);
    }

    private void replaceLeaderboard(UUID searchRunId, List<Ranking> rankings, Instant updatedAt) {
        jdbcTemplate.update("DELETE FROM leaderboard_entries WHERE search_run_id = ?", searchRunId);
        for (Ranking ranking : rankings) {
            jdbcTemplate.update(
                    """
                    INSERT INTO leaderboard_entries (
                        search_run_id, experiment_id, rank, score, return_pct,
                        max_drawdown_pct, total_trades, win_rate_pct, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    searchRunId,
                    ranking.experimentId(),
                    ranking.rank(),
                    ranking.metrics().score(),
                    ranking.metrics().totalReturnPct(),
                    ranking.metrics().maxDrawdownPct(),
                    ranking.metrics().totalTrades(),
                    ranking.metrics().winRatePct(),
                    timestamp(updatedAt));
        }
    }

    private void appendOutbox(DomainEventEnvelope<?> event, String routingKey) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type, schema_version,
                    payload_json, created_at, destination, routing_key, next_attempt_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                """,
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.schemaVersion(),
                json(event),
                timestamp(event.occurredAt()),
                DomainEventTopology.EXCHANGE,
                routingKey,
                timestamp(event.occurredAt()));
    }

    private static EvaluationMetrics metrics(ResultSet resultSet) throws SQLException {
        return new EvaluationMetrics(
                resultSet.getBigDecimal("total_return_pct"),
                resultSet.getBigDecimal("max_drawdown_pct"),
                resultSet.getInt("total_trades"),
                resultSet.getBigDecimal("win_rate_pct"),
                resultSet.getBigDecimal("score"));
    }

    private static boolean sameRankings(List<Ranking> left, List<Ranking> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            Ranking a = left.get(index);
            Ranking b = right.get(index);
            if (a.rank() != b.rank()
                    || !a.experimentId().equals(b.experimentId())
                    || !sameMetrics(a.metrics(), b.metrics())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameMetrics(EvaluationMetrics left, EvaluationMetrics right) {
        return left.totalTrades() == right.totalTrades()
                && left.totalReturnPct().compareTo(right.totalReturnPct()) == 0
                && left.maxDrawdownPct().compareTo(right.maxDrawdownPct()) == 0
                && left.winRatePct().compareTo(right.winRatePct()) == 0
                && left.score().compareTo(right.score()) == 0;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot serialize domain event", exception);
        }
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record PersistedCompletion(
            UUID searchRunId,
            String status,
            String evaluatorVersion,
            EvaluationMetrics metrics) {}
}
