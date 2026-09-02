package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestJobIdentifiers;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStateMachine;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.infrastructure.experiment.messaging.BacktestJobTopology;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSearchRunRepository implements SearchRunRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSearchRunRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void create(SearchRun run, ExecutionConfig executionConfig) {
        Integer datasets = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM market_datasets
                WHERE symbol = ? AND timeframe = ? AND from_time = ? AND to_time = ?
                  AND dataset_version = ? AND checksum = ?
                """,
                Integer.class,
                run.context().dataset().symbol(),
                run.context().dataset().timeframe().exchangeCode(),
                timestamp(run.context().dataset().from()),
                timestamp(run.context().dataset().to()),
                run.context().dataset().datasetVersion(),
                run.context().dataset().checksum());
        if (datasets == null || datasets != 1) {
            throw new IllegalArgumentException("search dataset does not exist: "
                    + run.context().dataset().checksum());
        }
        jdbcTemplate.update(
                """
                INSERT INTO search_runs (
                    id, status, symbol, timeframe, generator_type, generator_version, random_seed,
                    search_config_json, stop_conditions_json, execution_config_json,
                    created_at, cancel_requested
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb),
                          CAST(? AS jsonb), ?, false)
                """,
                run.id(),
                run.status().name(),
                run.context().dataset().symbol(),
                run.context().dataset().timeframe().exchangeCode(),
                run.generatorType(),
                run.generatorVersion(),
                run.context().randomSeed(),
                json(run.context()),
                json(run.context().stopConditions()),
                json(executionConfig),
                timestamp(run.createdAt()));
    }

    @Override
    @Transactional
    public void transition(
            UUID searchRunId,
            SearchRunStatus expected,
            SearchRunStatus target,
            SearchStopReason stopReason,
            Instant at) {
        SearchRunStateMachine.requireTransition(expected, target);
        boolean terminal = target == SearchRunStatus.COMPLETED
                || target == SearchRunStatus.CANCELLED
                || target == SearchRunStatus.FAILED;
        int updated = jdbcTemplate.update(
                """
                UPDATE search_runs
                SET status = ?,
                    started_at = CASE WHEN ? = 'RUNNING' THEN COALESCE(started_at, ?) ELSE started_at END,
                    ended_at = CASE WHEN ? THEN ? ELSE ended_at END,
                    stop_reason = COALESCE(?, stop_reason)
                WHERE id = ? AND status = ?
                """,
                target.name(),
                target.name(),
                timestamp(at),
                terminal,
                timestamp(at),
                stopReason == null ? null : stopReason.name(),
                searchRunId,
                expected.name());
        if (updated != 1) {
            throw new ConcurrentModificationException(
                    "search-run transition lost: " + searchRunId + " " + expected + " -> " + target);
        }
    }

    @Override
    @Transactional
    public void finishGeneration(UUID searchRunId, SearchStopReason stopReason, Instant at) {
        SearchRunStateMachine.requireTransition(SearchRunStatus.RUNNING, SearchRunStatus.EVALUATING);
        int updated = jdbcTemplate.update(
                """
                UPDATE search_runs
                SET status = 'EVALUATING', stop_reason = ?
                WHERE id = ? AND status = 'RUNNING'
                """,
                stopReason == null ? null : stopReason.name(),
                searchRunId);
        if (updated != 1) {
            throw new ConcurrentModificationException(
                    "search-run generation handoff lost: " + searchRunId);
        }
        completeIfAllJobsTerminal(searchRunId, at);
    }

    @Override
    @Transactional
    public int appendCandidatesAndCreateJobs(
            SearchRun run,
            ExecutionConfig executionConfig,
            JobDispatchMetadata dispatchMetadata,
            List<CandidateStrategy> candidates,
            Instant generatedAt) {
        List<CandidateStrategy> batch = List.copyOf(candidates);
        if (batch.isEmpty()) {
            return 0;
        }
        int persisted = 0;
        for (CandidateStrategy candidate : batch) {
            int inserted = jdbcTemplate.update(
                    """
                    INSERT INTO candidates (
                        id, search_run_id, candidate_hash, candidate_spec_json, created_at
                    ) VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                    ON CONFLICT DO NOTHING
                    """,
                    candidate.candidateId(),
                    run.id(),
                    candidate.candidateHash(),
                    json(candidate),
                    timestamp(generatedAt));
            if (inserted == 1) {
                persistExperimentAndDispatchIntent(
                        run, executionConfig, dispatchMetadata, candidate, generatedAt);
                persisted++;
            }
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE search_runs
                SET generated_candidates = generated_candidates + ?,
                    persisted_candidates = persisted_candidates + ?
                WHERE id = ? AND status = 'RUNNING'
                """,
                batch.size(),
                persisted,
                run.id());
        if (updated != 1) {
            throw new ConcurrentModificationException("search run is not RUNNING: " + run.id());
        }
        return persisted;
    }

    @Override
    @Transactional
    public boolean cancel(UUID searchRunId, Instant cancelledAt) {
        int runUpdated = jdbcTemplate.update(
                """
                UPDATE search_runs
                SET cancel_requested = true, status = 'CANCELLED', ended_at = ?,
                    stop_reason = 'USER_CANCELLED'
                WHERE id = ? AND status IN ('CREATED', 'RUNNING', 'PAUSED', 'EVALUATING')
                """,
                timestamp(cancelledAt),
                searchRunId);
        if (runUpdated == 0) {
            return false;
        }
        jdbcTemplate.update(
                """
                UPDATE backtest_jobs
                SET status = 'CANCELLED', worker_id = NULL, lease_until = NULL,
                    last_error = 'cancelled by search run'
                WHERE search_run_id = ?
                  AND status IN ('PENDING_DISPATCH', 'QUEUED', 'RETRY_PENDING')
                """,
                searchRunId);
        jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = 'CANCELLED', completed_at = ?, failure_code = NULL,
                    failure_message = 'cancelled by search run', version = version + 1
                WHERE search_run_id = ?
                  AND status IN ('CREATED', 'QUEUED', 'RETRY_PENDING')
                """,
                timestamp(cancelledAt),
                searchRunId);
        jdbcTemplate.update(
                """
                UPDATE outbox_events outbox
                SET cancelled_at = ?, last_error = 'cancelled before worker execution',
                    claimed_by = NULL, claimed_until = NULL
                FROM backtest_jobs job
                WHERE job.outbox_event_id = outbox.event_id
                  AND job.search_run_id = ? AND job.status = 'CANCELLED'
                  AND outbox.published_at IS NULL AND outbox.cancelled_at IS NULL
                """,
                timestamp(cancelledAt),
                searchRunId);
        return true;
    }

    @Override
    @Transactional
    public void recordEvaluation(UUID searchRunId, BigDecimal score) {
        if (score == null) {
            throw new IllegalArgumentException("score must not be null");
        }
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
                WHERE id = ? AND status IN ('RUNNING', 'EVALUATING')
                """,
                score,
                score,
                score,
                searchRunId);
        if (updated != 1) {
            throw new ConcurrentModificationException("cannot record evaluation for search run: " + searchRunId);
        }
    }

    @Override
    public Map<UUID, BigDecimal> awaitCandidateFitness(
            UUID searchRunId,
            List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> expected = Set.copyOf(candidateIds);
        BigDecimal lowestFitness = BigDecimal.valueOf(-1_000_000);
        // Duplicate genomes are skipped by UNIQUE(search_run_id, candidate_hash) and never get jobs.
        // Wait only on persisted candidates (AD-23); treat skipped IDs as lowest fitness.
        long deadline = System.nanoTime() + 300_000_000_000L;
        while (System.nanoTime() < deadline) {
            Map<UUID, BigDecimal> fitness = new HashMap<>();
            Set<UUID> terminal = new HashSet<>();
            Set<UUID> persistedIds = new HashSet<>(jdbcTemplate.query(
                    "SELECT id FROM candidates WHERE search_run_id = ?",
                    (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                    searchRunId));
            for (UUID candidateId : expected) {
                if (!persistedIds.contains(candidateId)) {
                    fitness.put(candidateId, lowestFitness);
                    terminal.add(candidateId);
                }
            }
            List<FitnessRow> rows = jdbcTemplate.query(
                    """
                    SELECT candidate.id AS candidate_id, job.status, metrics.score
                    FROM candidates candidate
                    JOIN experiments experiment
                      ON experiment.candidate_id = candidate.id
                     AND experiment.search_run_id = candidate.search_run_id
                    JOIN backtest_jobs job ON job.experiment_id = experiment.id
                    LEFT JOIN evaluation_metrics metrics ON metrics.experiment_id = experiment.id
                    WHERE candidate.search_run_id = ?
                    """,
                    (resultSet, rowNumber) -> new FitnessRow(
                            resultSet.getObject("candidate_id", UUID.class),
                            resultSet.getString("status"),
                            resultSet.getBigDecimal("score")),
                    searchRunId);
            for (FitnessRow row : rows) {
                if (!expected.contains(row.candidateId())) {
                    continue;
                }
                if (row.score() != null) {
                    fitness.put(row.candidateId(), row.score());
                    terminal.add(row.candidateId());
                } else if ("FAILED".equals(row.status()) || "CANCELLED".equals(row.status())) {
                    fitness.put(row.candidateId(), lowestFitness);
                    terminal.add(row.candidateId());
                }
            }
            if (terminal.containsAll(expected)) {
                return Map.copyOf(fitness);
            }
            Boolean cancelled = jdbcTemplate.queryForObject(
                    "SELECT cancel_requested FROM search_runs WHERE id = ?",
                    Boolean.class,
                    searchRunId);
            if (Boolean.TRUE.equals(cancelled)) {
                for (UUID candidateId : expected) {
                    fitness.putIfAbsent(candidateId, lowestFitness);
                }
                return Map.copyOf(fitness);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for candidate fitness", interrupted);
            }
        }
        throw new IllegalStateException("timed out waiting for candidate fitness: " + searchRunId);
    }

    @Override
    @Transactional
    public void fail(UUID searchRunId, String failureCode, String failureMessage, Instant failedAt) {
        jdbcTemplate.update(
                """
                UPDATE search_runs
                SET status = 'FAILED', ended_at = ?, stop_reason = 'FAILED',
                    failure_code = ?, failure_message = ?
                WHERE id = ? AND status IN ('CREATED', 'RUNNING', 'PAUSED', 'EVALUATING')
                """,
                timestamp(failedAt),
                failureCode,
                failureMessage,
                searchRunId);
    }

    @Override
    public Optional<SearchRunSummary> findSummary(UUID searchRunId) {
        return jdbcTemplate.query(
                        """
                        SELECT sr.id, sr.status, sr.generator_type, sr.generator_version, sr.search_config_json,
                               created_at, started_at, ended_at, cancel_requested,
                               generated_candidates, persisted_candidates, best_score,
                               no_improvement_iterations, stop_reason, failure_code, failure_message,
                               (SELECT count(*) FROM backtest_jobs j
                                WHERE j.search_run_id = sr.id
                                  AND j.status IN ('PENDING_DISPATCH', 'RETRY_PENDING'))
                                   AS pending_dispatch_jobs,
                               (SELECT count(*) FROM backtest_jobs j
                                WHERE j.search_run_id = sr.id AND j.status = 'QUEUED') AS queued_jobs
                               ,(SELECT count(*) FROM backtest_jobs j
                                WHERE j.search_run_id = sr.id AND j.status = 'RUNNING') AS running_jobs
                               ,(SELECT count(*) FROM backtest_jobs j
                                WHERE j.search_run_id = sr.id AND j.status = 'COMPLETED') AS completed_jobs
                               ,(SELECT count(*) FROM backtest_jobs j
                                WHERE j.search_run_id = sr.id AND j.status = 'FAILED') AS failed_jobs
                               ,(SELECT count(*) FROM backtest_jobs j
                                WHERE j.search_run_id = sr.id AND j.status = 'CANCELLED') AS cancelled_jobs
                        FROM search_runs sr
                        WHERE sr.id = ? AND sr.generator_type <> 'manual'
                        """,
                        (resultSet, rowNumber) -> summary(resultSet),
                        searchRunId)
                .stream()
                .findFirst();
    }

    private SearchRunSummary summary(ResultSet resultSet) throws SQLException {
        SearchContext context = read(resultSet.getString("search_config_json"), SearchContext.class);
        SearchRun run = new SearchRun(
                resultSet.getObject("id", UUID.class),
                SearchRunStatus.valueOf(resultSet.getString("status")),
                context,
                resultSet.getString("generator_type"),
                resultSet.getString("generator_version"),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "started_at"),
                nullableInstant(resultSet, "ended_at"),
                resultSet.getBoolean("cancel_requested"));
        String reason = resultSet.getString("stop_reason");
        return new SearchRunSummary(
                run,
                resultSet.getLong("generated_candidates"),
                resultSet.getLong("persisted_candidates"),
                resultSet.getLong("pending_dispatch_jobs"),
                resultSet.getLong("queued_jobs"),
                resultSet.getLong("running_jobs"),
                resultSet.getLong("completed_jobs"),
                resultSet.getLong("failed_jobs"),
                resultSet.getLong("cancelled_jobs"),
                resultSet.getBigDecimal("best_score"),
                resultSet.getInt("no_improvement_iterations"),
                reason == null ? null : SearchStopReason.valueOf(reason),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"));
    }

    private void completeIfAllJobsTerminal(UUID searchRunId, Instant completedAt) {
        SearchRunStateMachine.requireTransition(SearchRunStatus.EVALUATING, SearchRunStatus.COMPLETED);
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

    private void persistExperimentAndDispatchIntent(
            SearchRun run,
            ExecutionConfig executionConfig,
            JobDispatchMetadata dispatchMetadata,
            CandidateStrategy candidate,
            Instant createdAt) {
        UUID experimentId = BacktestJobIdentifiers.experimentId(run.id(), candidate.candidateId());
        UUID eventId = BacktestJobIdentifiers.dispatchEventId(run.id(), candidate.candidateId());
        GeneratorSnapshot generator = new GeneratorSnapshot(
                run.generatorType(),
                run.generatorVersion(),
                Map.of(
                        "strategyTypes", run.context().strategyTypes(),
                        "strategyVersions", run.context().strategyVersions(),
                        "parameterSpace", run.context().parameterSpace(),
                        "combinationPolicy", run.context().combinationPolicy()),
                run.context().randomSeed());
        BacktestJob job = new BacktestJob(
                new BacktestCommand(
                        experimentId,
                        candidate.candidateId(),
                        run.context().dataset(),
                        executionConfig),
                0,
                run.id().toString());

        jdbcTemplate.update(
                """
                INSERT INTO experiments (
                    id, candidate_id, search_run_id, status, dataset_ref_json,
                    execution_config_json, strategy_snapshot_json, combination_policy_json,
                    generator_snapshot_json, evaluator_version, code_commit, build_version,
                    version
                ) VALUES (?, ?, ?, 'CREATED', CAST(? AS jsonb), CAST(? AS jsonb),
                          CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, 0)
                """,
                experimentId,
                candidate.candidateId(),
                run.id(),
                json(run.context().dataset()),
                json(executionConfig),
                json(candidate.strategies()),
                json(candidate.combinationPolicy()),
                json(generator),
                dispatchMetadata.evaluatorVersion(),
                dispatchMetadata.codeCommit(),
                dispatchMetadata.buildVersion());
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type, schema_version,
                    payload_json, created_at, destination, routing_key, next_attempt_at
                ) VALUES (?, 'Experiment', ?, ?, 1, CAST(? AS jsonb), ?, ?, ?, ?)
                """,
                eventId,
                experimentId,
                BacktestJobTopology.OUTBOX_EVENT_TYPE,
                json(job),
                timestamp(createdAt),
                BacktestJobTopology.JOB_EXCHANGE,
                BacktestJobTopology.JOB_ROUTING_KEY,
                timestamp(createdAt));
        jdbcTemplate.update(
                """
                INSERT INTO backtest_jobs (
                    job_id, experiment_id, search_run_id, outbox_event_id, status,
                    payload_json, created_at
                ) VALUES (?, ?, ?, ?, 'PENDING_DISPATCH', CAST(? AS jsonb), ?)
                """,
                experimentId,
                experimentId,
                run.id(),
                eventId,
                json(job),
                timestamp(createdAt));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot serialize search configuration", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot deserialize search configuration", exception);
        }
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record FitnessRow(UUID candidateId, String status, BigDecimal score) {}
}
