package com.cryptolab.infrastructure.experiment.messaging;

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
public class JdbcBacktestJobOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcBacktestJobOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<BacktestJobOutboxMessage> claimBatch(
            String publisherId,
            int batchSize,
            Duration lease,
            Instant now) {
        if (publisherId == null || publisherId.isBlank()) {
            throw new IllegalArgumentException("publisherId must not be blank");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("lease must be positive");
        }
        return jdbcTemplate.query(
                """
                WITH selected AS (
                    SELECT event_id
                    FROM outbox_events
                    WHERE published_at IS NULL
                      AND cancelled_at IS NULL
                      AND event_type IN (?, ?)
                      AND next_attempt_at <= ?
                      AND (claimed_until IS NULL OR claimed_until < ?)
                    ORDER BY created_at, event_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_events outbox
                SET claimed_by = ?, claimed_until = ?
                FROM selected
                WHERE outbox.event_id = selected.event_id
                RETURNING outbox.event_id, outbox.aggregate_id, outbox.event_type,
                          outbox.schema_version, outbox.payload_json::text, outbox.destination,
                          outbox.routing_key, outbox.attempt_count, outbox.created_at
                """,
                (resultSet, rowNumber) -> message(resultSet),
                BacktestJobTopology.OUTBOX_EVENT_TYPE,
                BacktestJobTopology.RETRY_OUTBOX_EVENT_TYPE,
                timestamp(now),
                timestamp(now),
                batchSize,
                publisherId.trim(),
                timestamp(now.plus(lease)));
    }

    @Transactional
    public boolean markConfirmed(
            UUID eventId,
            String eventType,
            String publisherId,
            Instant confirmedAt) {
        boolean retry = BacktestJobTopology.RETRY_OUTBOX_EVENT_TYPE.equals(eventType);
        String expectedJobStatus = retry ? "RETRY_PENDING" : "PENDING_DISPATCH";
        String expectedExperimentStatus = retry ? "RETRY_PENDING" : "CREATED";
        int outboxUpdated = jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET published_at = ?, attempt_count = attempt_count + 1,
                    last_error = NULL, claimed_by = NULL, claimed_until = NULL
                WHERE event_id = ? AND event_type = ?
                  AND published_at IS NULL AND claimed_by = ?
                """,
                timestamp(confirmedAt),
                eventId,
                eventType,
                publisherId);
        if (outboxUpdated != 1) {
            if (isCancelled(eventId)) {
                return false;
            }
            throw new ConcurrentModificationException("outbox confirmation claim lost: " + eventId);
        }
        int jobUpdated = jdbcTemplate.update(
                """
                UPDATE backtest_jobs
                SET status = 'QUEUED', queued_at = COALESCE(queued_at, ?),
                    dispatch_attempts = dispatch_attempts + 1,
                    last_error = NULL
                WHERE outbox_event_id = ? AND status = ?
                """,
                timestamp(confirmedAt),
                eventId,
                expectedJobStatus);
        if (jobUpdated != 1) {
            throw new ConcurrentModificationException("backtest job is not pending dispatch: " + eventId);
        }
        int experimentUpdated = jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = 'QUEUED', version = version + 1
                WHERE id = (SELECT experiment_id FROM backtest_jobs WHERE outbox_event_id = ?)
                  AND status = ?
                """,
                eventId,
                expectedExperimentStatus);
        if (experimentUpdated != 1) {
            throw new ConcurrentModificationException("experiment is not awaiting dispatch: " + eventId);
        }
        return true;
    }

    @Transactional
    public void recordFailure(
            UUID eventId,
            String publisherId,
            String error,
            Instant nextAttemptAt) {
        String safeError = error == null || error.isBlank() ? "broker publish failed" : error;
        int outboxUpdated = jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET attempt_count = attempt_count + 1, last_error = ?, next_attempt_at = ?,
                    claimed_by = NULL, claimed_until = NULL
                WHERE event_id = ? AND published_at IS NULL AND claimed_by = ?
                """,
                safeError,
                timestamp(nextAttemptAt),
                eventId,
                publisherId);
        if (outboxUpdated != 1) {
            if (isCancelled(eventId)) {
                return;
            }
            throw new ConcurrentModificationException("outbox failure claim lost: " + eventId);
        }
        jdbcTemplate.update(
                """
                UPDATE backtest_jobs
                SET dispatch_attempts = dispatch_attempts + 1, last_error = ?
                WHERE outbox_event_id = ? AND status IN ('PENDING_DISPATCH', 'RETRY_PENDING')
                """,
                safeError,
                eventId);
    }

    private boolean isCancelled(UUID eventId) {
        Integer cancelled = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_id = ? AND cancelled_at IS NOT NULL",
                Integer.class,
                eventId);
        return cancelled != null && cancelled == 1;
    }

    private static BacktestJobOutboxMessage message(ResultSet resultSet) throws SQLException {
        return new BacktestJobOutboxMessage(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getInt("schema_version"),
                resultSet.getString("payload_json"),
                resultSet.getString("destination"),
                resultSet.getString("routing_key"),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
