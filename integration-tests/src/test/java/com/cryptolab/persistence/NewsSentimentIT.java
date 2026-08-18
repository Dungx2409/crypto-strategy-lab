package com.cryptolab.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.infrastructure.news.adapter.DeterministicKeywordSentimentAnalyzer;
import com.cryptolab.infrastructure.news.adapter.persistence.JdbcNewsStore;
import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.domain.NewsHealthStatus;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.NewsProvider;
import com.cryptolab.news.port.NewsTelemetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class NewsSentimentIT {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    private static JdbcTemplate jdbc;
    private static JdbcNewsStore store;

    @BeforeAll
    static void migrate() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcNewsStore(jdbc);
    }

    @Test
    void persistsIdempotentNewsAndVersionedPredictionsAndReadsThemDuringProviderFailure() {
        MutableProvider provider = new MutableProvider();
        provider.items = List.of(item("news-positive", "Bitcoin adoption rally"),
                item("news-negative", "Exchange hack losses"));
        NewsCollector collector = new NewsCollector(
                provider,
                new DeterministicKeywordSentimentAnalyzer(CLOCK),
                store,
                NewsTelemetry.noop(),
                CLOCK,
                Duration.ofHours(24),
                2);

        assertThat(collector.collect().analyzed()).isEqualTo(2);
        assertThat(collector.collect().analyzed()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM news_items", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sentiment_predictions", Integer.class))
                .isEqualTo(2);
        assertThat(collector.latest(10)).hasSize(2).allSatisfy(insight -> {
            assertThat(insight.sentiment()).isPresent();
            assertThat(insight.sentiment().orElseThrow().model().name())
                    .isEqualTo("deterministic-keyword");
            assertThat(insight.sentiment().orElseThrow().model().version()).isEqualTo("keyword-v1");
        });

        provider.failure = new IllegalStateException("news service down");
        assertThat(collector.collect().providerStatus()).isEqualTo(NewsHealthStatus.DOWN);
        assertThat(collector.latest(10)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sentiment_predictions", Integer.class))
                .isEqualTo(2);
    }

    private static NewsItem item(String id, String text) {
        return new NewsItem(
                id,
                "Integration Feed",
                text,
                "https://example.test/" + id,
                NOW.minusSeconds(id.endsWith("positive") ? 30 : 60),
                text,
                "input-v1");
    }

    private static final class MutableProvider implements NewsProvider {
        private List<NewsItem> items = List.of();
        private RuntimeException failure;

        @Override
        public List<NewsItem> fetchSince(Instant since) {
            if (failure != null) {
                throw failure;
            }
            return items;
        }
    }
}
