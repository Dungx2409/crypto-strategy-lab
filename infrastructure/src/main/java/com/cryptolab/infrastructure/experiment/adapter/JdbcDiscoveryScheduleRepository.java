package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.DiscoverySchedule;
import com.cryptolab.experiment.domain.DiscoveryScheduleStatus;
import com.cryptolab.experiment.domain.DiscoveryScheduleVersion;
import com.cryptolab.experiment.port.DiscoveryScheduleRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
public class JdbcDiscoveryScheduleRepository implements DiscoveryScheduleRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDiscoveryScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public DiscoverySchedule create(DiscoverySchedule schedule) {
        jdbcTemplate.update("""
                INSERT INTO discovery_schedules (
                    id, account_id, symbol, timeframe, lookback_seconds, initial_capital,
                candidate_limit, interval_seconds, status, next_run_at, active_search_run_id,
                last_search_run_id, completed_runs, last_error, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, schedule.id(), schedule.accountId(), schedule.symbol(), schedule.timeframe().exchangeCode(),
                schedule.lookback().toSeconds(), schedule.initialCapital(), schedule.candidateLimit(),
            schedule.interval().toSeconds(), schedule.status().name(), utc(schedule.nextRunAt()),
            schedule.activeSearchRunId(), schedule.lastSearchRunId(), schedule.completedRuns(), schedule.lastError(),
                utc(schedule.createdAt()), utc(schedule.updatedAt()));
        insertVersion(schedule.id(), 1, schedule.symbol(), schedule.timeframe(), schedule.lookback(),
                schedule.initialCapital(), schedule.candidateLimit(), schedule.interval(), schedule.createdAt());
        return schedule;
    }

    @Override
    public List<DiscoverySchedule> findAll(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT * FROM discovery_schedules WHERE account_id = ? ORDER BY created_at DESC
                """, this::schedule, accountId);
    }

    @Override
    public Optional<DiscoverySchedule> find(UUID accountId, UUID scheduleId) {
        return jdbcTemplate.query("""
                SELECT * FROM discovery_schedules WHERE account_id = ? AND id = ?
                """, this::schedule, accountId, scheduleId).stream().findFirst();
    }

    @Override
    public List<DiscoverySchedule> findRunning() {
        return jdbcTemplate.query("""
                SELECT * FROM discovery_schedules WHERE active_search_run_id IS NOT NULL
                """, this::schedule);
    }

    @Override
    public List<DiscoverySchedule> findDue(Instant now, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM discovery_schedules
                WHERE status = 'ACTIVE' AND active_search_run_id IS NULL AND next_run_at <= ?
                ORDER BY next_run_at LIMIT ?
                """, this::schedule, utc(now), limit);
    }

    @Override
    public boolean claim(UUID scheduleId, UUID searchRunId, Instant nextRunAt, Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE discovery_schedules
            SET active_search_run_id = ?, last_search_run_id = ?, next_run_at = ?, last_error = NULL, updated_at = ?
                WHERE id = ? AND status = 'ACTIVE' AND active_search_run_id IS NULL AND next_run_at <= ?
            """, searchRunId, searchRunId, utc(nextRunAt), utc(updatedAt), scheduleId, utc(updatedAt)) == 1;
    }

    @Override
    public void completeRun(UUID scheduleId, Instant updatedAt) {
        jdbcTemplate.update("""
                UPDATE discovery_schedules
                SET active_search_run_id = NULL, completed_runs = completed_runs + 1,
                    last_error = NULL, updated_at = ? WHERE id = ?
                """, utc(updatedAt), scheduleId);
    }

    @Override
    public void failRun(UUID scheduleId, String error, Instant updatedAt) {
        jdbcTemplate.update("""
                UPDATE discovery_schedules
                SET active_search_run_id = NULL, last_error = ?, updated_at = ? WHERE id = ?
                """, error, utc(updatedAt), scheduleId);
    }

    @Override
    public boolean stop(UUID accountId, UUID scheduleId, Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE discovery_schedules SET status = 'STOPPED', updated_at = ?
                WHERE account_id = ? AND id = ?
                """, utc(updatedAt), accountId, scheduleId) == 1;
    }

    @Override
    public boolean start(UUID accountId, UUID scheduleId, Instant nextRunAt, Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE discovery_schedules
                SET status = 'ACTIVE', next_run_at = ?, last_error = NULL, updated_at = ?
                WHERE account_id = ? AND id = ? AND active_search_run_id IS NULL
                """, utc(nextRunAt), utc(updatedAt), accountId, scheduleId) == 1;
    }

    @Override
    @Transactional
    public DiscoverySchedule updateConfiguration(
            UUID accountId,
            UUID scheduleId,
            String symbol,
            Timeframe timeframe,
            Duration lookback,
            java.math.BigDecimal initialCapital,
            long candidateLimit,
            Duration interval,
            Instant updatedAt) {
        int changed = jdbcTemplate.update("""
                UPDATE discovery_schedules SET symbol = ?, timeframe = ?, lookback_seconds = ?,
                    initial_capital = ?, candidate_limit = ?, interval_seconds = ?,
                    next_run_at = ?, updated_at = ?
                WHERE account_id = ? AND id = ? AND active_search_run_id IS NULL
                """, symbol.trim().toUpperCase(), timeframe.exchangeCode(), lookback.toSeconds(),
                initialCapital, candidateLimit, interval.toSeconds(), utc(updatedAt), utc(updatedAt),
                accountId, scheduleId);
        if (changed != 1) {
            throw new IllegalArgumentException("Discovery schedule was not found or is running: " + scheduleId);
        }
        Integer version = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM discovery_schedule_versions WHERE schedule_id = ?",
                Integer.class, scheduleId);
        insertVersion(scheduleId, version, symbol, timeframe, lookback, initialCapital,
                candidateLimit, interval, updatedAt);
        return find(accountId, scheduleId).orElseThrow();
    }

    @Override
    public List<DiscoveryScheduleVersion> findVersions(UUID accountId, UUID scheduleId) {
        return jdbcTemplate.query("""
                SELECT v.version, v.symbol, v.timeframe, v.lookback_seconds, v.initial_capital,
                       v.candidate_limit, v.interval_seconds, v.created_at
                FROM discovery_schedule_versions v
                JOIN discovery_schedules s ON s.id = v.schedule_id
                WHERE s.account_id = ? AND v.schedule_id = ? ORDER BY version DESC
                """, (rs, row) -> new DiscoveryScheduleVersion(
                        scheduleId, rs.getInt("version"), rs.getString("symbol"),
                        Timeframe.fromExchangeCode(rs.getString("timeframe")),
                        Duration.ofSeconds(rs.getLong("lookback_seconds")),
                        rs.getBigDecimal("initial_capital"), rs.getLong("candidate_limit"),
                        Duration.ofSeconds(rs.getLong("interval_seconds")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                accountId, scheduleId);
    }

    @Override
    public void recoverInterrupted(Instant now) {
        jdbcTemplate.update("""
                UPDATE discovery_schedules
                SET active_search_run_id = NULL, next_run_at = ?,
                    last_error = 'API restarted while the discovery run was active', updated_at = ?
                WHERE active_search_run_id IS NOT NULL
                """, utc(now), utc(now));
    }

    private DiscoverySchedule schedule(ResultSet rs, int row) throws SQLException {
        return new DiscoverySchedule(
                rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("symbol"), Timeframe.fromExchangeCode(rs.getString("timeframe")),
                Duration.ofSeconds(rs.getLong("lookback_seconds")), rs.getBigDecimal("initial_capital"),
                rs.getLong("candidate_limit"), Duration.ofSeconds(rs.getLong("interval_seconds")),
                DiscoveryScheduleStatus.valueOf(rs.getString("status")), instant(rs, "next_run_at"),
                rs.getObject("active_search_run_id", UUID.class), rs.getObject("last_search_run_id", UUID.class),
                rs.getLong("completed_runs"), rs.getString("last_error"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private void insertVersion(
            UUID scheduleId,
            int version,
            String symbol,
            Timeframe timeframe,
            Duration lookback,
            java.math.BigDecimal initialCapital,
            long candidateLimit,
            Duration interval,
            Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO discovery_schedule_versions (
                    schedule_id, version, symbol, timeframe, lookback_seconds,
                    initial_capital, candidate_limit, interval_seconds, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, scheduleId, version, symbol.trim().toUpperCase(), timeframe.exchangeCode(),
                lookback.toSeconds(), initialCapital, candidateLimit, interval.toSeconds(), utc(createdAt));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
