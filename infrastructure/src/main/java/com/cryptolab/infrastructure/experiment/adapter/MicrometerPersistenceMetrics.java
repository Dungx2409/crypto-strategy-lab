package com.cryptolab.infrastructure.experiment.adapter;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class MicrometerPersistenceMetrics {

    private final JdbcTemplate jdbcTemplate;

    public MicrometerPersistenceMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("crypto.outbox.pending", this, metrics -> metrics.count(
                        "SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND cancelled_at IS NULL"))
                .description("Unpublished non-cancelled transactional outbox events")
                .register(registry);
    }

    private double count(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0 : value.doubleValue();
        } catch (DataAccessException unavailableDatabase) {
            return Double.NaN;
        }
    }
}
