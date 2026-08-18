package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.infrastructure.marketdata.adapter.persistence.JdbcCandleStore;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import java.math.BigDecimal;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CandleStoreIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    private static JdbcCandleStore store;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrate() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcCandleStore(jdbcTemplate);
    }

    @Test
    void uniqueKeyPreventsDuplicateHistoricalAndRealtimeCandles() {
        Candle first = candle("2026-08-18T01:00:00Z", "105");
        Candle repeatedWithDifferentClose = candle("2026-08-18T01:00:00Z", "106");
        Candle second = candle("2026-08-18T01:05:00Z", "107");

        assertThat(store.saveIfAbsent(first)).isTrue();
        assertThat(store.saveIfAbsent(repeatedWithDifferentClose)).isFalse();
        assertThat(store.saveIfAbsent(second)).isTrue();

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM candles", Integer.class);
        assertThat(count).isEqualTo(2);
        assertThat(store.findLatest(new TradingPair("BTCUSDT"), Timeframe.M5, 10))
                .extracting(Candle::openTime)
                .containsExactly(first.openTime(), second.openTime());
        assertThat(store.findLastOpenTime(new TradingPair("BTCUSDT"), Timeframe.M5))
                .contains(second.openTime());
    }

    private static Candle candle(String openTime, String close) {
        return new Candle(
                "BTCUSDT",
                Timeframe.M5,
                Instant.parse(openTime),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("90"),
                new BigDecimal(close),
                new BigDecimal("12"));
    }
}
