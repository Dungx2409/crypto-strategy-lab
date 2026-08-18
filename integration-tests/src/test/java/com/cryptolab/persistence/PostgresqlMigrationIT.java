package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgresqlMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
                    .withDatabaseName("crypto_strategy_lab")
                    .withUsername("crypto_lab")
                    .withPassword("crypto_lab_test");

    @Test
    void migratesAnEmptyPostgresqlDatabaseAndCreatesRequiredTables() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(7);

        Set<String> tables = readPublicTables();
        assertThat(tables)
                .contains(
                        "candles",
                        "market_datasets",
                        "search_runs",
                        "candidates",
                        "experiments",
                        "backtest_jobs",
                        "experiment_signals",
                        "trades",
                        "evaluation_metrics",
                        "leaderboard_entries",
                        "news_items",
                        "sentiment_predictions",
                        "outbox_events",
                        "processed_events");
    }

    private Set<String> readPublicTables() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("table_name"));
            }
        }
        return tables;
    }
}
