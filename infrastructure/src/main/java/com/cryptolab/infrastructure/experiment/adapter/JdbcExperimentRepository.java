package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.Experiment;
import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.ExperimentStateMachine;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.experiment.domain.LeaderboardEntry;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.RecordedSignal;
import com.cryptolab.experiment.domain.Trade;
import com.cryptolab.experiment.port.CandidateProvider;
import com.cryptolab.experiment.port.ExperimentRepository;
import com.cryptolab.experiment.port.MarketDatasetProvider;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcExperimentRepository
        implements ExperimentRepository, CandidateProvider, MarketDatasetProvider {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcExperimentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void create(ExperimentPlan plan) {
        persistDataset(plan.dataset(), plan.createdAt());
        persistSearchRun(plan);
        persistCandidate(plan);
        jdbcTemplate.update(
                """
                INSERT INTO experiments (
                    id, candidate_id, search_run_id, status, dataset_ref_json,
                    execution_config_json, strategy_snapshot_json, combination_policy_json,
                    generator_snapshot_json, evaluator_version, code_commit, build_version,
                    reproduction_of_id, version
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb),
                          CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, 0)
                """,
                plan.experimentId(),
                plan.candidate().candidateId(),
                plan.searchRunId(),
                ExperimentStatus.CREATED.name(),
                json(plan.dataset().reference()),
                json(plan.executionConfig()),
                json(plan.candidate().strategies()),
                json(plan.candidate().combinationPolicy()),
                json(plan.generator()),
                plan.evaluatorVersion(),
                plan.codeCommit(),
                plan.buildVersion(),
                plan.reproductionOfExperimentId());
    }

    @Override
    @Transactional
    public void transition(
            UUID experimentId,
            ExperimentStatus expected,
            ExperimentStatus target,
            Instant at) {
        ExperimentStateMachine.requireTransition(expected, target);
        int updated = jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = ?,
                    started_at = CASE WHEN ? = 'RUNNING' THEN ? ELSE started_at END,
                    version = version + 1
                WHERE id = ? AND status = ?
                """,
                target.name(),
                target.name(),
                timestamp(at),
                experimentId,
                expected.name());
        if (updated != 1) {
            throw new ConcurrentModificationException(
                    "experiment transition lost: " + experimentId + " " + expected + " -> " + target);
        }
    }

    @Override
    @Transactional
    public void complete(
            UUID experimentId,
            BacktestResult result,
            Evaluation evaluation,
            Instant completedAt) {
        if (!experimentId.equals(result.experimentId()) || !experimentId.equals(evaluation.experimentId())) {
            throw new IllegalArgumentException("result/evaluation experiment identity mismatch");
        }
        persistSignals(experimentId, result.signals());
        persistTrades(experimentId, result.trades());
        persistMetrics(experimentId, evaluation.metrics());
        int updated = jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = ?, completed_at = ?, version = version + 1
                WHERE id = ? AND status = ?
                """,
                ExperimentStatus.COMPLETED.name(),
                timestamp(completedAt),
                experimentId,
                ExperimentStatus.RUNNING.name());
        if (updated != 1) {
            throw new ConcurrentModificationException("experiment completion lost: " + experimentId);
        }
        jdbcTemplate.update(
                """
                UPDATE search_runs
                SET status = 'COMPLETED', ended_at = ?
                WHERE id = (SELECT search_run_id FROM experiments WHERE id = ?)
                """,
                timestamp(completedAt),
                experimentId);
    }

    @Override
    @Transactional
    public void fail(UUID experimentId, String failureCode, String failureMessage, Instant failedAt) {
        jdbcTemplate.update(
                """
                UPDATE experiments
                SET status = ?, completed_at = ?, failure_code = ?, failure_message = ?, version = version + 1
                WHERE id = ? AND status IN ('CREATED', 'QUEUED', 'RUNNING')
                """,
                ExperimentStatus.FAILED.name(),
                timestamp(failedAt),
                failureCode,
                failureMessage,
                experimentId);
    }

    @Override
    public List<Evaluation> findCompletedEvaluations(UUID searchRunId) {
        return jdbcTemplate.query(
                """
                SELECT e.id, e.evaluator_version, e.completed_at,
                       m.total_return_pct, m.max_drawdown_pct, m.total_trades,
                       m.win_rate_pct, m.score
                FROM experiments e
                JOIN evaluation_metrics m ON m.experiment_id = e.id
                WHERE e.search_run_id = ? AND e.status = 'COMPLETED'
                """,
                (resultSet, rowNumber) -> new Evaluation(
                        resultSet.getObject("id", UUID.class),
                        metrics(resultSet),
                        resultSet.getString("evaluator_version"),
                        instant(resultSet, "completed_at")),
                searchRunId);
    }

    @Override
    @Transactional
    public void replaceLeaderboard(UUID searchRunId, List<Ranking> rankings, Instant updatedAt) {
        jdbcTemplate.update("DELETE FROM leaderboard_entries WHERE search_run_id = ?", searchRunId);
        for (Ranking ranking : rankings) {
            jdbcTemplate.update(
                    """
                    INSERT INTO leaderboard_entries (
                        search_run_id, experiment_id, rank, score, return_pct,
                        max_drawdown_pct, total_trades, win_rate_pct, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    searchRunId,
                    ranking.experimentId(),
                    ranking.rank(),
                    ranking.metrics().score(),
                    ranking.metrics().totalReturnPct(),
                    ranking.metrics().maxDrawdownPct(),
                    ranking.metrics().totalTrades(),
                    ranking.metrics().winRatePct(),
                    timestamp(updatedAt));
        }
    }

    @Override
    public List<LeaderboardEntry> findLeaderboard(UUID searchRunId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT l.search_run_id, l.rank, l.experiment_id, l.score, l.return_pct,
                       l.max_drawdown_pct, l.total_trades, l.win_rate_pct, c.candidate_spec_json
                FROM leaderboard_entries l
                JOIN experiments e ON e.id = l.experiment_id
                JOIN candidates c ON c.id = e.candidate_id
                WHERE l.search_run_id = ?
                ORDER BY l.rank
                LIMIT ?
                """,
                (resultSet, rowNumber) -> {
                    CandidateStrategy candidate = read(
                            resultSet.getString("candidate_spec_json"), CandidateStrategy.class);
                    EvaluationMetrics values = new EvaluationMetrics(
                            resultSet.getBigDecimal("return_pct"),
                            resultSet.getBigDecimal("max_drawdown_pct"),
                            resultSet.getInt("total_trades"),
                            resultSet.getBigDecimal("win_rate_pct"),
                            resultSet.getBigDecimal("score"));
                    Ranking ranking = new Ranking(
                            resultSet.getInt("rank"),
                            resultSet.getObject("experiment_id", UUID.class),
                            values);
                    String summary = candidate.strategies().stream()
                            .map(strategy -> strategy.type())
                            .reduce((left, right) -> left + "+" + right)
                            .orElseThrow();
                    return new LeaderboardEntry(
                            resultSet.getObject("search_run_id", UUID.class), ranking, summary);
                },
                searchRunId,
                limit);
    }

    @Override
    public Optional<ExperimentDetails> findDetails(UUID experimentId) {
        List<DetailsRow> rows = jdbcTemplate.query(
                """
                SELECT e.*, c.candidate_spec_json, m.total_return_pct, m.max_drawdown_pct,
                       m.total_trades, m.win_rate_pct, m.score,
                       (SELECT l.rank FROM leaderboard_entries l
                        WHERE l.search_run_id = e.search_run_id AND l.experiment_id = e.id) AS leaderboard_rank
                FROM experiments e
                JOIN candidates c ON c.id = e.candidate_id
                LEFT JOIN evaluation_metrics m ON m.experiment_id = e.id
                WHERE e.id = ?
                """,
                (resultSet, rowNumber) -> mapDetailsRow(resultSet),
                experimentId);
        return rows.stream().findFirst().map(row -> new ExperimentDetails(
                row.experiment(),
                row.candidate(),
                row.generator(),
                findSignals(experimentId, row.candidate()),
                findTrades(experimentId),
                row.metrics(),
                row.rank()));
    }

    @Override
    public Optional<ExperimentPlan> findPlan(UUID experimentId) {
        return findDetails(experimentId).map(details -> {
            Experiment experiment = details.experiment();
            MarketDataset dataset = getDataset(experiment.dataset());
            Instant reconstructedCreatedAt = experiment.startedAt() == null
                    ? Instant.EPOCH
                    : experiment.startedAt();
            return new ExperimentPlan(
                    experiment.id(),
                    experiment.searchRunId(),
                    details.candidate(),
                    dataset,
                    experiment.executionConfig(),
                    details.generator(),
                    experiment.evaluatorVersion(),
                    experiment.codeCommit(),
                    experiment.buildVersion(),
                    experiment.reproductionOfExperimentId(),
                    reconstructedCreatedAt);
        });
    }

    @Override
    public CandidateStrategy getCandidate(UUID candidateId) {
        List<CandidateStrategy> values = jdbcTemplate.query(
                "SELECT candidate_spec_json FROM candidates WHERE id = ?",
                (resultSet, rowNumber) -> read(
                        resultSet.getString("candidate_spec_json"), CandidateStrategy.class),
                candidateId);
        return values.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("candidate not found: " + candidateId));
    }

    @Override
    public MarketDataset getDataset(MarketDatasetRef reference) {
        List<DatasetRow> datasets = jdbcTemplate.query(
                """
                SELECT id, symbol, timeframe, from_time, to_time, dataset_version, checksum
                FROM market_datasets
                WHERE symbol = ? AND timeframe = ? AND from_time = ? AND to_time = ?
                  AND dataset_version = ? AND checksum = ?
                """,
                (resultSet, rowNumber) -> new DatasetRow(
                        resultSet.getObject("id", UUID.class),
                        new MarketDatasetRef(
                                resultSet.getString("symbol"),
                                Timeframe.fromExchangeCode(resultSet.getString("timeframe")),
                                instant(resultSet, "from_time"),
                                instant(resultSet, "to_time"),
                                resultSet.getString("dataset_version"),
                                resultSet.getString("checksum"))),
                reference.symbol(),
                reference.timeframe().exchangeCode(),
                timestamp(reference.from()),
                timestamp(reference.to()),
                reference.datasetVersion(),
                reference.checksum());
        DatasetRow dataset = datasets.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("market dataset not found: " + reference.checksum()));
        List<Candle> candles = jdbcTemplate.query(
                """
                SELECT sequence_no, open_time, open, high, low, close, volume
                FROM market_dataset_candles
                WHERE dataset_id = ?
                ORDER BY sequence_no
                """,
                (resultSet, rowNumber) -> new Candle(
                        dataset.reference().symbol(),
                        dataset.reference().timeframe(),
                        instant(resultSet, "open_time"),
                        resultSet.getBigDecimal("open"),
                        resultSet.getBigDecimal("high"),
                        resultSet.getBigDecimal("low"),
                        resultSet.getBigDecimal("close"),
                        resultSet.getBigDecimal("volume")),
                dataset.id());
        return new MarketDataset(dataset.id(), dataset.reference(), candles);
    }

    private void persistDataset(MarketDataset dataset, Instant createdAt) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO market_datasets (
                    id, symbol, timeframe, from_time, to_time, dataset_version, checksum, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (
                    symbol, timeframe, from_time, to_time, dataset_version, checksum
                ) DO NOTHING
                """,
                dataset.id(),
                dataset.reference().symbol(),
                dataset.reference().timeframe().exchangeCode(),
                timestamp(dataset.reference().from()),
                timestamp(dataset.reference().to()),
                dataset.reference().datasetVersion(),
                dataset.reference().checksum(),
                timestamp(createdAt));
        if (inserted == 1) {
            for (int index = 0; index < dataset.candles().size(); index++) {
                Candle candle = dataset.candles().get(index);
                jdbcTemplate.update(
                        """
                        INSERT INTO market_dataset_candles (
                            dataset_id, sequence_no, open_time, open, high, low, close, volume
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        dataset.id(),
                        index,
                        timestamp(candle.openTime()),
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close(),
                        candle.volume());
            }
        }
    }

    private void persistSearchRun(ExperimentPlan plan) {
        jdbcTemplate.update(
                """
                INSERT INTO search_runs (
                    id, status, symbol, timeframe, generator_type, generator_version, random_seed,
                    search_config_json, stop_conditions_json, execution_config_json,
                    created_at, started_at, cancel_requested
                ) VALUES (?, 'RUNNING', ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb),
                          CAST(? AS jsonb), ?, ?, false)
                ON CONFLICT (id) DO NOTHING
                """,
                plan.searchRunId(),
                plan.dataset().reference().symbol(),
                plan.dataset().reference().timeframe().exchangeCode(),
                plan.generator().type(),
                plan.generator().version(),
                plan.generator().randomSeed(),
                json(plan.generator().configuration()),
                "{\"maxCandidates\":1}",
                json(plan.executionConfig()),
                timestamp(plan.createdAt()),
                timestamp(plan.createdAt()));
    }

    private void persistCandidate(ExperimentPlan plan) {
        jdbcTemplate.update(
                """
                INSERT INTO candidates (id, search_run_id, candidate_hash, candidate_spec_json, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (id) DO NOTHING
                """,
                plan.candidate().candidateId(),
                plan.searchRunId(),
                plan.candidate().candidateHash(),
                json(plan.candidate()),
                timestamp(plan.createdAt()));
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
                    UUID.randomUUID(),
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
                        exit_time, exit_price, quantity, fee, pnl
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    experimentId,
                    index,
                    timestamp(trade.entryTime()),
                    trade.entryPrice(),
                    timestamp(trade.exitTime()),
                    trade.exitPrice(),
                    trade.quantity(),
                    trade.fee(),
                    trade.pnl());
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

    private DetailsRow mapDetailsRow(ResultSet resultSet) throws SQLException {
        CandidateStrategy candidate = read(
                resultSet.getString("candidate_spec_json"), CandidateStrategy.class);
        MarketDatasetRef dataset = read(resultSet.getString("dataset_ref_json"), MarketDatasetRef.class);
        com.cryptolab.experiment.domain.ExecutionConfig execution = read(
                resultSet.getString("execution_config_json"),
                com.cryptolab.experiment.domain.ExecutionConfig.class);
        GeneratorSnapshot generator = read(
                resultSet.getString("generator_snapshot_json"), GeneratorSnapshot.class);
        Experiment experiment = new Experiment(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("candidate_id", UUID.class),
                resultSet.getObject("search_run_id", UUID.class),
                ExperimentStatus.valueOf(resultSet.getString("status")),
                dataset,
                execution,
                candidate.strategies(),
                candidate.combinationPolicy(),
                generator.type(),
                generator.version(),
                generator.randomSeed(),
                resultSet.getString("evaluator_version"),
                resultSet.getString("code_commit"),
                resultSet.getString("build_version"),
                resultSet.getObject("reproduction_of_id", UUID.class),
                nullableInstant(resultSet, "started_at"),
                nullableInstant(resultSet, "completed_at"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                resultSet.getLong("version"));
        EvaluationMetrics values = resultSet.getObject("score") == null ? null : metrics(resultSet);
        Integer rank = resultSet.getObject("leaderboard_rank") == null
                ? null
                : resultSet.getInt("leaderboard_rank");
        return new DetailsRow(experiment, candidate, generator, values, rank);
    }

    private List<RecordedSignal> findSignals(UUID experimentId, CandidateStrategy candidate) {
        Map<String, String> versions = new HashMap<>();
        candidate.strategies().forEach(strategy -> versions.put(strategy.type(), strategy.version()));
        versions.put("COMPOSITE", candidate.combinationPolicy().version());
        return jdbcTemplate.query(
                """
                SELECT strategy_type, signal_type, strength, signal_at, reason
                FROM experiment_signals
                WHERE experiment_id = ?
                ORDER BY sequence_no
                """,
                (resultSet, rowNumber) -> {
                    String strategyType = resultSet.getString("strategy_type");
                    return new RecordedSignal(
                            strategyType,
                            versions.getOrDefault(strategyType, "unknown"),
                            new Signal(
                                    SignalType.valueOf(resultSet.getString("signal_type")),
                                    resultSet.getBigDecimal("strength"),
                                    instant(resultSet, "signal_at"),
                                    resultSet.getString("reason")));
                },
                experimentId);
    }

    private List<Trade> findTrades(UUID experimentId) {
        return jdbcTemplate.query(
                """
                SELECT entry_time, entry_price, exit_time, exit_price, quantity, fee, pnl
                FROM trades
                WHERE experiment_id = ?
                ORDER BY sequence_no
                """,
                (resultSet, rowNumber) -> new Trade(
                        instant(resultSet, "entry_time"),
                        resultSet.getBigDecimal("entry_price"),
                        instant(resultSet, "exit_time"),
                        resultSet.getBigDecimal("exit_price"),
                        resultSet.getBigDecimal("quantity"),
                        resultSet.getBigDecimal("fee"),
                        resultSet.getBigDecimal("pnl")),
                experimentId);
    }

    private static EvaluationMetrics metrics(ResultSet resultSet) throws SQLException {
        return new EvaluationMetrics(
                resultSet.getBigDecimal("total_return_pct"),
                resultSet.getBigDecimal("max_drawdown_pct"),
                resultSet.getInt("total_trades"),
                resultSet.getBigDecimal("win_rate_pct"),
                resultSet.getBigDecimal("score"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot serialize immutable experiment snapshot", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot deserialize experiment snapshot as " + type.getSimpleName(), exception);
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

    private record DatasetRow(UUID id, MarketDatasetRef reference) {}

    private record DetailsRow(
            Experiment experiment,
            CandidateStrategy candidate,
            GeneratorSnapshot generator,
            EvaluationMetrics metrics,
            Integer rank) {}
}
