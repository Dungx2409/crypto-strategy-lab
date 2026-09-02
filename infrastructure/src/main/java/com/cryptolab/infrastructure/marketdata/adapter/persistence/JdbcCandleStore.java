package com.cryptolab.infrastructure.marketdata.adapter.persistence;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCandleStore implements CandleStore {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final String provider;

    @Autowired
    public JdbcCandleStore(
            JdbcTemplate jdbcTemplate,
            @Value("${crypto.market.provider:binance}") String provider) {
        this(jdbcTemplate, Clock.systemUTC(), provider);
    }

    public JdbcCandleStore(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC(), "binance");
    }

    public JdbcCandleStore(JdbcTemplate jdbcTemplate, Clock clock, String provider) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("BINANCE") && !normalized.equals("OKX")) {
            throw new IllegalArgumentException("provider must be BINANCE or OKX");
        }
        this.provider = normalized;
    }

    @Override
    public boolean saveIfAbsent(Candle candle) {
        int rows = jdbcTemplate.update(
                """
                INSERT INTO candles (
                    symbol, timeframe, open_time, open, high, low, close, volume, provider, received_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (provider, symbol, timeframe, open_time) DO NOTHING
                """,
                candle.symbol(),
                candle.timeframe().exchangeCode(),
                OffsetDateTime.ofInstant(candle.openTime(), ZoneOffset.UTC),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                provider,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return rows == 1;
    }

    @Override
    public List<Candle> findLatest(TradingPair pair, Timeframe timeframe, int limit) {
        List<Candle> descending = jdbcTemplate.query(
                """
                SELECT symbol, timeframe, open_time, open, high, low, close, volume
                FROM candles
                WHERE provider = ? AND symbol = ? AND timeframe = ?
                ORDER BY open_time DESC
                LIMIT ?
                """,
                this::mapCandle,
                provider,
                pair.symbol(),
                timeframe.exchangeCode(),
                limit);
        List<Candle> chronological = new ArrayList<>(descending);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    @Override
    public List<Candle> findBetween(
            TradingPair pair, Timeframe timeframe, Instant from, Instant to, int limit) {
        return jdbcTemplate.query(
                """
                SELECT symbol, timeframe, open_time, open, high, low, close, volume
                FROM candles
                WHERE provider = ? AND symbol = ? AND timeframe = ?
                  AND open_time >= ? AND open_time < ?
                ORDER BY open_time ASC
                LIMIT ?
                """,
                this::mapCandle,
                provider,
                pair.symbol(),
                timeframe.exchangeCode(),
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                limit);
    }

    @Override
    public Optional<Instant> findLastOpenTime(TradingPair pair, Timeframe timeframe) {
        List<Instant> values = jdbcTemplate.query(
                """
                SELECT open_time
                FROM candles
                WHERE provider = ? AND symbol = ? AND timeframe = ?
                ORDER BY open_time DESC
                LIMIT 1
                """,
                (resultSet, rowNumber) -> resultSet
                        .getObject("open_time", OffsetDateTime.class)
                        .toInstant(),
                provider,
                pair.symbol(),
                timeframe.exchangeCode());
        return values.stream().findFirst();
    }

    private Candle mapCandle(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Candle(
                resultSet.getString("symbol"),
                Timeframe.fromExchangeCode(resultSet.getString("timeframe")),
                resultSet.getObject("open_time", OffsetDateTime.class).toInstant(),
                resultSet.getBigDecimal("open"),
                resultSet.getBigDecimal("high"),
                resultSet.getBigDecimal("low"),
                resultSet.getBigDecimal("close"),
                resultSet.getBigDecimal("volume"));
    }
}
