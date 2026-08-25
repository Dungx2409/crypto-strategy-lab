package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.StrategyDraft;
import com.cryptolab.strategy.domain.StrategyDraftStatus;
import com.cryptolab.strategy.domain.UserStrategy;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import com.cryptolab.strategy.port.UserStrategyRepository;
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

@Repository
public class JdbcUserStrategyRepository implements UserStrategyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcUserStrategyRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public StrategyDraft createDraft(StrategyDraft draft) {
        jdbcTemplate.update("""
                INSERT INTO strategy_drafts
                    (id, account_id, prompt, idea, status, failure_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, draft.id(), draft.accountId(), draft.prompt(), draft.idea(), draft.status().name(),
                draft.failureMessage(), utc(draft.createdAt()), utc(draft.updatedAt()));
        return draft;
    }

    @Override
    public Optional<StrategyDraft> findDraft(UUID accountId, UUID draftId) {
        return jdbcTemplate.query("""
                SELECT * FROM strategy_drafts WHERE account_id = ? AND id = ?
                """, this::draft, accountId, draftId).stream().findFirst();
    }

    @Override
    public void updateDraft(
            UUID accountId, UUID draftId, StrategyDraftStatus status, String failureMessage, Instant updatedAt) {
        jdbcTemplate.update("""
                UPDATE strategy_drafts SET status = ?, failure_message = ?, updated_at = ?
                WHERE account_id = ? AND id = ?
                """, status.name(), failureMessage, utc(updatedAt), accountId, draftId);
    }

    @Override
    public UserStrategy saveVersion(
            UUID id, UUID accountId, UserStrategyDocument document, String sourcePrompt, Instant createdAt) {
        Integer version = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version), 0) + 1 FROM user_strategies
                WHERE account_id = ? AND normalized_name = lower(?)
                """, Integer.class, accountId, document.name());
        jdbcTemplate.update("""
                INSERT INTO user_strategies
                    (id, account_id, name, normalized_name, version, document_json, source_prompt, created_at)
                VALUES (?, ?, ?, lower(?), ?, CAST(? AS jsonb), ?, ?)
                """, id, accountId, document.name(), document.name(), version,
                json(document), sourcePrompt, utc(createdAt));
        return new UserStrategy(id, accountId, version, document, sourcePrompt, createdAt);
    }

    @Override
    public List<UserStrategy> findAll(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT * FROM user_strategies WHERE account_id = ?
                ORDER BY normalized_name, version DESC
                """, this::strategy, accountId);
    }

    @Override
    public Optional<UserStrategy> find(UUID accountId, UUID strategyId) {
        return jdbcTemplate.query("""
                SELECT * FROM user_strategies WHERE account_id = ? AND id = ?
                """, this::strategy, accountId, strategyId).stream().findFirst();
    }

    @Override
    public boolean delete(UUID accountId, UUID strategyId) {
        return jdbcTemplate.update(
                "DELETE FROM user_strategies WHERE account_id = ? AND id = ?", accountId, strategyId) == 1;
    }

    private StrategyDraft draft(ResultSet rs, int row) throws SQLException {
        return new StrategyDraft(
                rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("prompt"), rs.getString("idea"),
                StrategyDraftStatus.valueOf(rs.getString("status")), rs.getString("failure_message"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private UserStrategy strategy(ResultSet rs, int row) throws SQLException {
        try {
            return new UserStrategy(
                    rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                    rs.getInt("version"),
                    objectMapper.readValue(rs.getString("document_json"), UserStrategyDocument.class),
                    rs.getString("source_prompt"), instant(rs, "created_at"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Stored strategy JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Strategy document could not be serialized", exception);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
