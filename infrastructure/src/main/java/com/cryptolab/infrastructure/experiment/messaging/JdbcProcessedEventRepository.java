package com.cryptolab.infrastructure.experiment.messaging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isProcessed(String consumerName, UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_name = ? AND event_id = ?",
                Integer.class,
                consumerName,
                eventId);
        return count != null && count == 1;
    }

    @Transactional
    public boolean markProcessed(String consumerName, UUID eventId, Instant processedAt) {
        return jdbcTemplate.update(
                        """
                        INSERT INTO processed_events (consumer_name, event_id, processed_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (consumer_name, event_id) DO NOTHING
                        """,
                        consumerName,
                        eventId,
                        OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC))
                == 1;
    }
}
