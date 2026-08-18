package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.MarketDatasetService;
import com.cryptolab.infrastructure.experiment.adapter.JdbcMarketDatasetRepository;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
class MarketDatasetMaterializationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    private static JdbcTemplate jdbc;
    private static MarketDatasetService service;

    @BeforeAll
    static void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        service = new MarketDatasetService(
                new JdbcMarketDatasetRepository(jdbc),
                Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC),
                () -> UUID.randomUUID());
    }

    @Test
    void repeatMaterializationReusesTheImmutableDatasetWithoutDuplicateCandles() {
        var first = service.materialize("BTCUSDT", Timeframe.M5, "dashboard-v1", candles());
        var second = service.materialize("BTCUSDT", Timeframe.M5, "dashboard-v1", candles());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.reference()).isEqualTo(first.reference());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM market_datasets", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM market_dataset_candles", Integer.class)).isEqualTo(2);
    }

    private static List<Candle> candles() {
        return List.of(candle(0), candle(1));
    }

    private static Candle candle(int index) {
        return new Candle(
                "BTCUSDT",
                Timeframe.M5,
                Instant.parse("2026-08-18T00:00:00Z").plusSeconds(index * 300L),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("90"),
                new BigDecimal("105"),
                BigDecimal.TEN);
    }
}
