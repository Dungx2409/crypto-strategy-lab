package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.port.MarketDatasetRepository;
import com.cryptolab.marketdata.domain.Candle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMarketDatasetRepository implements MarketDatasetRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketDatasetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public MarketDataset save(MarketDataset dataset, Instant createdAt) {
        var reference = dataset.reference();
        jdbcTemplate.update(
                """
                INSERT INTO market_datasets (
                    id, symbol, timeframe, from_time, to_time, dataset_version, checksum, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (symbol, timeframe, from_time, to_time, dataset_version, checksum)
                DO NOTHING
                """,
                dataset.id(),
                reference.symbol(),
                reference.timeframe().exchangeCode(),
                timestamp(reference.from()),
                timestamp(reference.to()),
                reference.datasetVersion(),
                reference.checksum(),
                timestamp(createdAt));
        UUID datasetId = jdbcTemplate.query(
                        """
                        SELECT id FROM market_datasets
                        WHERE symbol = ? AND timeframe = ? AND from_time = ? AND to_time = ?
                          AND dataset_version = ? AND checksum = ?
                        """,
                        (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                        reference.symbol(),
                        reference.timeframe().exchangeCode(),
                        timestamp(reference.from()),
                        timestamp(reference.to()),
                        reference.datasetVersion(),
                        reference.checksum())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("materialized dataset was not found"));
        for (int sequence = 0; sequence < dataset.candles().size(); sequence++) {
            Candle candle = dataset.candles().get(sequence);
            jdbcTemplate.update(
                    """
                    INSERT INTO market_dataset_candles (
                        dataset_id, sequence_no, open_time, open, high, low, close, volume
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """,
                    datasetId,
                    sequence,
                    timestamp(candle.openTime()),
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    candle.close(),
                    candle.volume());
        }
        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM market_dataset_candles WHERE dataset_id = ?",
                Integer.class,
                datasetId);
        if (persisted == null || persisted != dataset.candles().size()) {
            throw new IllegalStateException("materialized dataset candle count does not match input");
        }
        return new MarketDataset(datasetId, reference, dataset.candles());
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
