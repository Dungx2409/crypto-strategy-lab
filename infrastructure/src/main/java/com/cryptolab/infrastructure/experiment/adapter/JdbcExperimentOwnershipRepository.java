package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.port.ExperimentOwnershipRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class JdbcExperimentOwnershipRepository implements ExperimentOwnershipRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcExperimentOwnershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void assign(UUID experimentId, UUID accountId, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO experiment_owners (experiment_id, account_id, created_at)
                VALUES (?, ?, ?) ON CONFLICT (experiment_id) DO NOTHING
                """, experimentId, accountId, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    @Override
    public Optional<UUID> ownerOf(UUID experimentId) {
        return jdbcTemplate.query(
                "SELECT account_id FROM experiment_owners WHERE experiment_id = ?",
                (rs, row) -> rs.getObject(1, UUID.class), experimentId).stream().findFirst();
    }

    @Override
    public List<UUID> findExperimentIds(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT experiment_id FROM experiment_owners
                WHERE account_id = ? ORDER BY created_at DESC
                """, (rs, row) -> rs.getObject(1, UUID.class), accountId);
    }
}
