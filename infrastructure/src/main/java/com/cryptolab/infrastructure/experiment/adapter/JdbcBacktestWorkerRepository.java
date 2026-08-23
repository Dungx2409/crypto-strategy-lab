package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestJobClaim;
import com.cryptolab.experiment.domain.BacktestJobClaimDecision;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.RecordedSignal;
import com.cryptolab.experiment.domain.Trade;
import com.cryptolab.experiment.port.BacktestWorkerRepository;
import com.cryptolab.infrastructure.experiment.messaging.BacktestJobTopology;
import com.cryptolab.infrastructure.experiment.messaging.DomainEventTopology;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBacktestWorkerRepository implements BacktestWorkerRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcBacktestWorkerRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public BacktestJobClaim claim(
            UUID experimentId,
            String workerId,
            Duration lease,
            Instant claimedAt) {
        if (experimentId == null) {
            throw new IllegalArgumentException("experimentId must not be null");
        }
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("lease must be positive");
        }
        List<ClaimedRow> claimed = jdbcTemplate.query(
                """
                UPDATE backtest_jobs job
                SET status = 'RUNNING', worker_id = ?, lease_until = ?,
                    started_at = COALESCE(job.started_at, ?),
                    execution_attempts = job.execution_attempts + 1
                FROM experiments experiment
                WHERE job.experiment_id = ?
                  AND experiment.id = job.experiment_id
                  AND (
                      (job.status = 'QUEUED' AND experiment.status = 'QUEUED')
                      OR (
                          job.status = 'RUNNING' AND experiment.status = 'RUNNING'
                          AND job.lease_until < ?
                      )
                  )
                RETURNING job.search_run_id, job.payload_json::text, job.retry_count
                """,
                (resultSet, rowNumber) -> new ClaimedRow(
                        resultSet.getObject("search_run_id", UUID.class),
                        read(resultSet.getString("payload_json"), BacktestJob.class),
                        resultSet.getInt("retry_count")),
                workerId.trim(),
                timestamp(claimedAt.plus(lease)),
                timestamp(claimedAt),
                experimentId,
                timestamp(claimedAt));
        if (!claimed.isEmpty()) {
            int experimentUpdated = jdbcTemplate.update(
                    """
                    UPDATE experiments
                    SET status = 'RUNNING', started_at = COALESCE(started_at, ?), version = version + 1
                    WHERE id = ? AND status IN ('QUEUED', 'RUNNING')
                    """,
                    timestamp(claimedAt),
                    experimentId);
            if (experimentUpdated != 1) {
                throw new ConcurrentModificationException("experiment claim lost: " + experimentId);
            }
            ClaimedRow row = claimed.getFirst();
            return new BacktestJobClaim(
                    BacktestJobClaimDecision.CLAIMED,
                    experimentId,
                    row.searchRunId(),
                    row.job(),
                    row.retryCount(),
                    workerId.trim());
        }
        return currentDecision(experimentId);
    }

    @Override
    @Transactional
    public void complete(
            BacktestJobClaim claim,
            BacktestResult result,
            Evaluation evaluation,
            DomainEventEnvelope<BacktestCompletedEvent> completedEvent,
            Instant completedAt) {
        requireClaim(claim);
        if (!claim.experimentId().equals(result.experimentId())
                || !claim.experimentId().equals(evaluation.experimentId())
                || !claim.experimentId().equals(completedEvent.aggregateId())) {
            throw new IllegalArgumentException("worker completion identity mismatch");
        }
        int jobUpdated = jdbcTemplate.update(
                """
                UPDATE backtest_jobs
                SET status = 'COMPLETED', completed_at = ?, worker_id = NULL,
                    lease_until = NULL, last_error = NULL
                WHERE experiment_id = ? AND status = 'RUNNING' AND worker_id = ?
                """,
                timestamp(completedAt),
                claim.experimentId(),
                claim.workerId());
        if (jobUpdated != 1) {
            throw new ConcurrentModificationException("backtest job completion claim lost: " + claim.experimentId());
        }
        int experimentUpdated = jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = 'COMPLETED', completed_at = ?, version = version + 1
                WHERE id = ? AND status = 'RUNNING'
                """,
                timestamp(completedAt),
                claim.experimentId());
        if (experimentUpdated != 1) {
            throw new ConcurrentModificationException("experiment completion claim lost: " + claim.experimentId());
        }
        persistSignals(claim.experimentId(), result.signals());
        persistTrades(claim.experimentId(), result.trades());
        persistMetrics(claim.experimentId(), evaluation.metrics());
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type, schema_version,
                    payload_json, created_at, destination, routing_key, next_attempt_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                """,
                completedEvent.eventId(),
                completedEvent.aggregateType(),
                completedEvent.aggregateId(),
                completedEvent.eventType(),
                completedEvent.schemaVersion(),
                json(completedEvent),
                timestamp(completedEvent.occurredAt()),
                DomainEventTopology.EXCHANGE,
                DomainEventTopology.BACKTEST_COMPLETED_ROUTING_KEY,
                timestamp(completedEvent.occurredAt()));
        completeSearchRunIfDrained(claim.searchRunId(), completedAt);
    }

    @Override
    @Transactional
    public boolean scheduleRetry(
            BacktestJobClaim claim,
            BacktestJob retryJob,
            String failureMessage,
            Instant retryAt,
            Instant failedAt) {
        requireClaim(claim);
        int nextRetry = claim.retryCount() + 1;
        if (nextRetry > 3 || retryJob.attempt() != nextRetry) {
            throw new IllegalArgumentException("retry count must advance by one and not exceed 3");
        }
        Boolean searchCancelled = jdbcTemplate.queryForObject(
                "SELECT status = 'CANCELLED' FROM search_runs WHERE id = ?",
                Boolean.class,
                claim.searchRunId());
        if (Boolean.TRUE.equals(searchCancelled)) {
            cancelActiveClaim(claim, failedAt);
            return false;
        }
        UUID retryEventId = UUID.nameUUIDFromBytes(
                (BacktestJobTopology.RETRY_OUTBOX_EVENT_TYPE + ":"
                                + claim.experimentId() + ":" + nextRetry)
                        .getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type, schema_version,
                    payload_json, created_at, destination, routing_key, next_attempt_at
                ) VALUES (?, 'Experiment', ?, ?, 1, CAST(? AS jsonb), ?, ?, ?, ?)
                """,
                retryEventId,
                claim.experimentId(),
                BacktestJobTopology.RETRY_OUTBOX_EVENT_TYPE,
                json(retryJob),
                timestamp(failedAt),
                BacktestJobTopology.JOB_EXCHANGE,
                BacktestJobTopology.JOB_ROUTING_KEY,
                timestamp(retryAt));
        int jobUpdated = jdbcTemplate.update(
                """
                UPDATE backtest_jobs
                SET status = 'RETRY_PENDING', retry_count = ?, payload_json = CAST(? AS jsonb),
                    outbox_event_id = ?, last_error = ?, worker_id = NULL, lease_until = NULL
                WHERE experiment_id = ? AND status = 'RUNNING' AND worker_id = ?
                """,
                nextRetry,
                json(retryJob),
                retryEventId,
                safeError(failureMessage),
                claim.experimentId(),
                claim.workerId());
        if (jobUpdated != 1) {
            throw new ConcurrentModificationException("backtest retry claim lost: " + claim.experimentId());
        }
        int experimentUpdated = jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = 'RETRY_PENDING', version = version + 1,
                    failure_code = NULL, failure_message = ?
                WHERE id = ? AND status = 'RUNNING'
                """,
                safeError(failureMessage),
                claim.experimentId());
        if (experimentUpdated != 1) {
            throw new ConcurrentModificationException("experiment retry claim lost: " + claim.experimentId());
        }
        return true;
    }

    private void cancelActiveClaim(BacktestJobClaim claim, Instant cancelledAt) {
        int jobUpdated = jdbcTemplate.update(
                """
                UPDATE backtest_jobs
                SET status = 'CANCELLED', worker_id = NULL, lease_until = NULL,
                    last_error = 'search cancelled during execution'
                WHERE experiment_id = ? AND status = 'RUNNING' AND worker_id = ?
                """,
                claim.experimentId(),
                claim.workerId());
        int experimentUpdated = jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = 'CANCELLED', completed_at = ?, failure_code = NULL,
                    failure_message = 'search cancelled during execution', version = version + 1
                WHERE id = ? AND status = 'RUNNING'
                """,
                timestamp(cancelledAt),
                claim.experimentId());
        if (jobUpdated != 1 || experimentUpdated != 1) {
            throw new ConcurrentModificationException(
                    "cannot cancel active worker claim: " + claim.experimentId());
        }
        completeSearchRunIfDrained(claim.searchRunId(), cancelledAt);
    }

    @Override
    @Transactional
    public void failPermanently(
            BacktestJobClaim claim,
            String failureCode,
            String failureMessage,
            Instant failedAt) {
        requireClaim(claim);
        int jobUpdated = jdbcTemplate.update(
                """
                UPDATE backtest_jobs
                SET status = 'FAILED', last_error = ?, worker_id = NULL, lease_until = NULL
                WHERE experiment_id = ? AND status = 'RUNNING' AND worker_id = ?
                """,
                safeError(failureMessage),
                claim.experimentId(),
                claim.workerId());
        if (jobUpdated != 1) {
            throw new ConcurrentModificationException("backtest failure claim lost: " + claim.experimentId());
        }
        int experimentUpdated = jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = 'FAILED', completed_at = ?, failure_code = ?,
                    failure_message = ?, version = version + 1
                WHERE id = ? AND status = 'RUNNING'
                """,
                timestamp(failedAt),
                failureCode,
                safeError(failureMessage),
                claim.experimentId());
        if (experimentUpdated != 1) {
            throw new ConcurrentModificationException("experiment failure claim lost: " + claim.experimentId());
        }
        completeSearchRunIfDrained(claim.searchRunId(), failedAt);
    }

    private void completeSearchRunIfDrained(UUID searchRunId, Instant completedAt) {
        jdbcTemplate.update(
                """
                UPDATE search_runs sr
                SET status = 'COMPLETED', ended_at = ?
                WHERE sr.id = ? AND sr.status = 'EVALUATING'
                  AND NOT EXISTS (
                      SELECT 1 FROM backtest_jobs job
                      WHERE job.search_run_id = sr.id
                        AND job.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
                  )
                """,
                timestamp(completedAt),
                searchRunId);
    }

    private BacktestJobClaim currentDecision(UUID experimentId) {
        List<StatusRow> states = jdbcTemplate.query(
                """
                SELECT job.status AS job_status, job.retry_count, experiment.status AS experiment_status
                FROM backtest_jobs job
                JOIN experiments experiment ON experiment.id = job.experiment_id
                WHERE job.experiment_id = ?
                """,
                (resultSet, rowNumber) -> new StatusRow(
                        resultSet.getString("job_status"),
                        resultSet.getString("experiment_status"),
                        resultSet.getInt("retry_count")),
                experimentId);
        if (states.isEmpty()) {
            return BacktestJobClaim.terminal(BacktestJobClaimDecision.NOT_FOUND, experimentId, 0);
        }
        StatusRow state = states.getFirst();
        BacktestJobClaimDecision decision = switch (state.jobStatus()) {
            case "PENDING_DISPATCH", "RETRY_PENDING" ->
                    BacktestJobClaimDecision.AWAITING_DISPATCH_CONFIRMATION;
            case "COMPLETED" -> BacktestJobClaimDecision.COMPLETED;
            case "FAILED" -> BacktestJobClaimDecision.FAILED;
            case "CANCELLED" -> BacktestJobClaimDecision.CANCELLED;
            default -> BacktestJobClaimDecision.IN_PROGRESS;
        };
        return BacktestJobClaim.terminal(decision, experimentId, state.retryCount());
    }

    private void persistSignals(UUID experimentId, List<RecordedSignal> signals) {
        for (int index = 0; index < signals.size(); index++) {
            RecordedSignal recorded = signals.get(index);
            jdbcTemplate.update(
                    """
                    INSERT INTO experiment_signals (
                        id, experiment_id, sequence_no, strategy_type, signal_type,
                        strength, signal_at, reason
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    artifactId(experimentId, "signal", index),
                    experimentId,
                    index,
                    recorded.strategyType(),
                    recorded.signal().type().name(),
                    recorded.signal().strength(),
                    timestamp(recorded.signal().at()),
                    recorded.signal().reason());
        }
    }

    private void persistTrades(UUID experimentId, List<Trade> trades) {
        for (int index = 0; index < trades.size(); index++) {
            Trade trade = trades.get(index);
            jdbcTemplate.update(
                    """
                    INSERT INTO trades (
                        id, experiment_id, sequence_no, entry_time, entry_price,
                        exit_time, exit_price, quantity, fee, pnl, direction, exit_reason
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    artifactId(experimentId, "trade", index),
                    experimentId,
                    index,
                    timestamp(trade.entryTime()),
                    trade.entryPrice(),
                    timestamp(trade.exitTime()),
                    trade.exitPrice(),
                    trade.quantity(),
                    trade.fee(),
                    trade.pnl(),
                    trade.direction().name(),
                    trade.exitReason().name());
        }
    }

    private void persistMetrics(UUID experimentId, EvaluationMetrics metrics) {
        jdbcTemplate.update(
                """
                INSERT INTO evaluation_metrics (
                    experiment_id, total_return_pct, max_drawdown_pct, total_trades,
                    win_rate_pct, score, metrics_json
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """,
                experimentId,
                metrics.totalReturnPct(),
                metrics.maxDrawdownPct(),
                metrics.totalTrades(),
                metrics.winRatePct(),
                metrics.score(),
                json(metrics));
    }

    private static void requireClaim(BacktestJobClaim claim) {
        if (claim == null || claim.decision() != BacktestJobClaimDecision.CLAIMED) {
            throw new IllegalArgumentException("an active worker claim is required");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot serialize worker state", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot deserialize durable backtest job", exception);
        }
    }

    private static UUID artifactId(UUID experimentId, String type, int index) {
        return UUID.nameUUIDFromBytes(
                (experimentId + ":" + type + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    private static String safeError(String error) {
        String value = error == null || error.isBlank() ? "worker execution failed" : error;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record ClaimedRow(UUID searchRunId, BacktestJob job, int retryCount) {}

    private record StatusRow(String jobStatus, String experimentStatus, int retryCount) {}
}
