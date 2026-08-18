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
public class JdbcDomainEventOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDomainEventOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<BacktestJobOutboxMessage> claimBatch(
            String publisherId,
            int batchSize,
            Duration lease,
            Instant now) {
        return jdbcTemplate.query(
                """
                WITH selected AS (
                    SELECT event_id
                    FROM outbox_events
                    WHERE published_at IS NULL
                      AND cancelled_at IS NULL
                      AND event_type IN (?, ?, ?)
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
                DomainEventTopology.BACKTEST_COMPLETED_EVENT_TYPE,
                DomainEventTopology.STRATEGY_EVALUATED_EVENT_TYPE,
                DomainEventTopology.LEADERBOARD_UPDATED_EVENT_TYPE,
                timestamp(now),
                timestamp(now),
                batchSize,
                publisherId,
                timestamp(now.plus(lease)));
    }

    @Transactional
    public void markConfirmed(UUID eventId, String eventType, String publisherId, Instant confirmedAt) {
        int updated = jdbcTemplate.update(
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
        if (updated != 1) {
            throw new ConcurrentModificationException("domain-event outbox claim lost: " + eventId);
        }
    }

    @Transactional
    public void recordFailure(
            UUID eventId,
            String eventType,
            String publisherId,
            String error,
            Instant nextAttemptAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET attempt_count = attempt_count + 1, last_error = ?, next_attempt_at = ?,
                    claimed_by = NULL, claimed_until = NULL
                WHERE event_id = ? AND event_type = ?
                  AND published_at IS NULL AND claimed_by = ?
                """,
                error,
                timestamp(nextAttemptAt),
                eventId,
                eventType,
                publisherId);
        if (updated != 1) {
            throw new ConcurrentModificationException("domain-event failure claim lost: " + eventId);
        }
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
