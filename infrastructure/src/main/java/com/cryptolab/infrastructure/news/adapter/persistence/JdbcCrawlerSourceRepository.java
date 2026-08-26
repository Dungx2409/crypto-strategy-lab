package com.cryptolab.infrastructure.news.adapter.persistence;

import com.cryptolab.news.domain.CrawlerSource;
import com.cryptolab.news.port.CrawlerSourceRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCrawlerSourceRepository implements CrawlerSourceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCrawlerSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public CrawlerSource create(CrawlerSource source) {
        jdbcTemplate.update(
                """
                INSERT INTO crawler_sources (
                    id, name, list_url, article_selector, title_selector, link_selector,
                    content_selector, published_at_selector, related_coins_selector,
                    enabled, version, consecutive_failures, last_error, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                values(source));
        saveVersion(source);
        return source;
    }

    @Override
    public List<CrawlerSource> findAll() {
        return jdbcTemplate.query("SELECT * FROM crawler_sources ORDER BY name, id", this::map);
    }

    @Override
    public List<CrawlerSource> findEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM crawler_sources WHERE enabled = true ORDER BY name, id", this::map);
    }

    @Override
    public Optional<CrawlerSource> find(UUID sourceId) {
        return jdbcTemplate.query(
                        "SELECT * FROM crawler_sources WHERE id = ?", this::map, sourceId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public CrawlerSource update(CrawlerSource source, int expectedVersion) {
        int changed = jdbcTemplate.update(
                """
                UPDATE crawler_sources SET
                    name = ?, list_url = ?, article_selector = ?, title_selector = ?,
                    link_selector = ?, content_selector = ?, published_at_selector = ?,
                    related_coins_selector = ?, enabled = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                source.name(), source.listUrl(), source.articleSelector(), source.titleSelector(),
                source.linkSelector(), source.contentSelector(), source.publishedAtSelector(),
                source.relatedCoinsSelector(), source.enabled(), source.version(), utc(source.updatedAt()),
                source.id(), expectedVersion);
        if (changed != 1) throw new ConcurrentModificationException("Crawler source changed concurrently");
        saveVersion(source);
        return find(source.id()).orElseThrow();
    }

    @Override
    public void recordSuccess(UUID sourceId, Instant at) {
        jdbcTemplate.update(
                """
                UPDATE crawler_sources SET consecutive_failures = 0, last_error = NULL, updated_at = ?
                WHERE id = ?
                """, utc(at), sourceId);
    }

    @Override
    public void recordFailure(UUID sourceId, String error, Instant at) {
        jdbcTemplate.update(
                """
                UPDATE crawler_sources SET consecutive_failures = consecutive_failures + 1,
                    last_error = ?, updated_at = ? WHERE id = ?
                """, error, utc(at), sourceId);
    }

    private CrawlerSource map(ResultSet rs, int rowNumber) throws SQLException {
        return new CrawlerSource(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("list_url"),
                rs.getString("article_selector"), rs.getString("title_selector"),
                rs.getString("link_selector"), rs.getString("content_selector"),
                rs.getString("published_at_selector"), rs.getString("related_coins_selector"),
                rs.getBoolean("enabled"), rs.getInt("version"), rs.getInt("consecutive_failures"),
                rs.getString("last_error"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private void saveVersion(CrawlerSource source) {
        jdbcTemplate.update(
                """
                INSERT INTO crawler_source_versions (
                    source_id, version, name, list_url, article_selector, title_selector,
                    link_selector, content_selector, published_at_selector,
                    related_coins_selector, enabled, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                source.id(), source.version(), source.name(), source.listUrl(),
                source.articleSelector(), source.titleSelector(), source.linkSelector(),
                source.contentSelector(), source.publishedAtSelector(),
                source.relatedCoinsSelector(), source.enabled(), utc(source.updatedAt()));
    }

    private static Object[] values(CrawlerSource source) {
        return new Object[] {source.id(), source.name(), source.listUrl(), source.articleSelector(),
            source.titleSelector(), source.linkSelector(), source.contentSelector(),
            source.publishedAtSelector(), source.relatedCoinsSelector(), source.enabled(),
            source.version(), source.consecutiveFailures(), source.lastError(),
            utc(source.createdAt()), utc(source.updatedAt())};
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
