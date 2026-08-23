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
        assertThat(result.migrationsExecuted).isEqualTo(16);

        Set<String> tables = readPublicTables();
        assertThat(tables)
                .contains(
                        "candles",
                        "market_datasets",
                        "market_dataset_sentiment_observations",
                        "search_runs",
                        "candidates",
                        "experiments",
                        "backtest_jobs",
                        "experiment_signals",
                        "trades",
                        "evaluation_metrics",
                        "leaderboard_entries",
                        "accounts",
                        "strategy_drafts",
                        "user_strategies",
                        "discovery_schedules",
                        "news_items",
                        "sentiment_predictions",
                        "outbox_events",
                        "processed_events");
        assertThat(readColumnLength("news_items", "input_version")).isEqualTo(128);
        assertThat(readColumnLength("sentiment_predictions", "input_version")).isEqualTo(128);
        assertThat(hasColumn("evaluation_metrics", "win_rate_pct")).isTrue();
        assertThat(hasColumn("leaderboard_entries", "win_rate_pct")).isTrue();
        assertThat(hasColumn("trades", "direction")).isTrue();
        assertThat(hasColumn("trades", "exit_reason")).isTrue();
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

    private int readColumnLength(String table, String column) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
                var statement = connection.prepareStatement(
                        """
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND column_name = ?
                        """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt("character_maximum_length");
            }
        }
    }

    private boolean hasColumn(String table, String column) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
                var statement = connection.prepareStatement(
                        """
                        SELECT count(*)
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND column_name = ?
                        """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1) == 1;
            }
        }
    }
}
