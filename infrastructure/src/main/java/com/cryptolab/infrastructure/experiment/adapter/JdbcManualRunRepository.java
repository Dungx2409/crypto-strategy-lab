package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.ManualRunBatch;
import com.cryptolab.experiment.domain.ManualRunChild;
import com.cryptolab.experiment.domain.ManualRunStatus;
import com.cryptolab.experiment.port.ManualRunRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcManualRunRepository implements ManualRunRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcManualRunRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ManualRunBatch create(ManualRunBatch batch) {
        jdbcTemplate.update(
                """
                INSERT INTO manual_run_batches (
                    id, account_id, strategy_id, symbol, from_time, to_time,
                    execution_config_json, status, cancel_requested, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, false, ?, ?)
                """,
                batch.id(),
                batch.accountId(),
                batch.strategyId(),
                batch.symbol(),
                utc(batch.from()),
                utc(batch.to()),
                json(batch.executionConfig()),
                batch.status().name(),
                utc(batch.createdAt()),
                utc(batch.updatedAt()));
        for (ManualRunChild child : batch.children()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO manual_run_children (
                        id, batch_id, timeframe, status, experiment_id, failure_message,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, ?, NULL, NULL, ?, ?)
                    """,
                    child.id(),
                    batch.id(),
                    child.timeframe().exchangeCode(),
                    child.status().name(),
                    utc(batch.createdAt()),
                    utc(batch.updatedAt()));
        }
        return batch;
    }

    @Override
    public Optional<ManualRunBatch> find(UUID accountId, UUID batchId) {
        return findWhere("id = ? AND account_id = ?", batchId, accountId);
    }

    @Override
    public Optional<ManualRunBatch> find(UUID batchId) {
        return findWhere("id = ?", batchId);
    }

    @Override
    public List<ManualRunBatch> findAll(UUID accountId) {
        return jdbcTemplate.query(
                """
                SELECT * FROM manual_run_batches
                WHERE account_id = ? ORDER BY created_at DESC
                """,
                (resultSet, rowNumber) -> batch(resultSet),
                accountId);
    }

    @Override
    public List<ManualRunBatch> findRecoverable() {
        return jdbcTemplate.query(
                """
                SELECT * FROM manual_run_batches
                WHERE status IN ('PREPARING', 'RUNNING')
                ORDER BY created_at, id
                """,
                (resultSet, rowNumber) -> batch(resultSet));
    }

    @Override
    public void markRunning(UUID batchId, Instant at) {
        jdbcTemplate.update(
                """
                UPDATE manual_run_batches SET status = 'RUNNING', updated_at = ?
                WHERE id = ? AND status = 'PREPARING' AND cancel_requested = false
                """,
                utc(at),
                batchId);
    }

    @Override
    public void completeChild(UUID childId, UUID experimentId, Instant at) {
        jdbcTemplate.update(
                """
                UPDATE manual_run_children
                SET status = 'COMPLETED', experiment_id = ?, updated_at = ?
                WHERE id = ? AND status = 'PREPARING'
                """,
                experimentId,
                utc(at),
                childId);
    }

    @Override
    public void failChild(UUID childId, String failureMessage, Instant at) {
        jdbcTemplate.update(
                """
                UPDATE manual_run_children
                SET status = 'FAILED', failure_message = ?, updated_at = ?
                WHERE id = ? AND status = 'PREPARING'
                """,
                failureMessage,
                utc(at),
                childId);
    }

    @Override
    @Transactional
    public void finish(UUID batchId, Instant at) {
        jdbcTemplate.update(
                """
                UPDATE manual_run_children SET status = 'CANCELLED', updated_at = ?
                WHERE batch_id = ? AND status = 'PREPARING'
                  AND EXISTS (
                    SELECT 1 FROM manual_run_batches b
                    WHERE b.id = ? AND b.cancel_requested = true
                  )
                """,
                utc(at),
                batchId,
                batchId);
        jdbcTemplate.update(
                """
                UPDATE manual_run_batches b
                SET status = CASE
                    WHEN cancel_requested THEN 'CANCELLED'
                    WHEN NOT EXISTS (
                        SELECT 1 FROM manual_run_children c
                        WHERE c.batch_id = b.id AND c.status <> 'FAILED'
                    ) THEN 'FAILED'
                    WHEN EXISTS (
                        SELECT 1 FROM manual_run_children c
                        WHERE c.batch_id = b.id AND c.status = 'FAILED'
                    ) THEN 'PARTIAL_FAILURE'
                    ELSE 'COMPLETED'
                END,
                updated_at = ?
                WHERE id = ?
                """,
                utc(at),
                batchId);
    }

    @Override
    public void requestCancellation(UUID accountId, UUID batchId, Instant at) {
        jdbcTemplate.update(
                """
                UPDATE manual_run_batches
                SET cancel_requested = true,
                    status = CASE WHEN status = 'PREPARING' THEN 'CANCELLED' ELSE status END,
                    updated_at = ?
                WHERE id = ? AND account_id = ?
                  AND status IN ('PREPARING', 'RUNNING')
                """,
                utc(at),
                batchId,
                accountId);
    }

    private Optional<ManualRunBatch> findWhere(String predicate, Object... arguments) {
        return jdbcTemplate.query(
                        "SELECT * FROM manual_run_batches WHERE " + predicate,
                        (resultSet, rowNumber) -> batch(resultSet),
                        arguments)
                .stream()
                .findFirst();
    }

    private ManualRunBatch batch(ResultSet resultSet) throws SQLException {
        UUID id = resultSet.getObject("id", UUID.class);
        List<ManualRunChild> children = jdbcTemplate.query(
                """
                SELECT * FROM manual_run_children WHERE batch_id = ? ORDER BY created_at, id
                """,
                this::child,
                id);
        return new ManualRunBatch(
                id,
                resultSet.getObject("account_id", UUID.class),
                resultSet.getObject("strategy_id", UUID.class),
                resultSet.getString("symbol"),
                instant(resultSet, "from_time"),
                instant(resultSet, "to_time"),
                read(resultSet.getString("execution_config_json"), ExecutionConfig.class),
                ManualRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("cancel_requested"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                children);
    }

    private ManualRunChild child(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ManualRunChild(
                resultSet.getObject("id", UUID.class),
                Timeframe.fromExchangeCode(resultSet.getString("timeframe")),
                ManualRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("experiment_id", UUID.class),
                resultSet.getString("failure_message"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("manual run configuration could not be serialized", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("manual run configuration is invalid", exception);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
