package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.domain.CrawlerTemplateVersion;
import com.cryptolab.news.port.CrawlerTemplateRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCrawlerTemplateRepository implements CrawlerTemplateRepository {
    private final JdbcTemplate jdbc;

    public JdbcCrawlerTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public CrawlerTemplateVersion create(
            UUID id, UUID accountId, String siteUrl, CrawlerSelectors selectors, Instant at) {
        jdbc.update("INSERT INTO crawler_templates (id, account_id, site_url, active_version, created_at) VALUES (?, ?, ?, 1, ?)",
                id, accountId, siteUrl, utc(at));
        insert(id, 1, selectors, "ACTIVE", null, at);
        return findCurrent(accountId, id).orElseThrow();
    }

    @Override
    public Optional<CrawlerTemplateVersion> findCurrent(UUID accountId, UUID templateId) {
        return jdbc.query("""
                SELECT t.id, t.account_id, t.site_url, v.* FROM crawler_templates t
                JOIN crawler_template_versions v ON v.template_id = t.id AND v.version = t.active_version
                WHERE t.account_id = ? AND t.id = ?
                """, this::row, accountId, templateId).stream().findFirst();
    }

    @Override
    public List<CrawlerTemplateVersion> findCurrentByAccount(UUID accountId) {
        return jdbc.query("""
                SELECT t.id, t.account_id, t.site_url, v.* FROM crawler_templates t
                JOIN crawler_template_versions v ON v.template_id = t.id AND v.version = t.active_version
                WHERE t.account_id = ? ORDER BY t.created_at DESC
                """, this::row, accountId);
    }

    @Override
    public List<CrawlerTemplateVersion> findVersions(UUID accountId, UUID templateId) {
        return jdbc.query("""
                SELECT t.id, t.account_id, t.site_url, v.* FROM crawler_templates t
                JOIN crawler_template_versions v ON v.template_id = t.id
                WHERE t.account_id = ? AND t.id = ? ORDER BY v.version DESC
                """, this::row, accountId, templateId);
    }

    @Override
    @Transactional
    public CrawlerTemplateVersion addRepair(
            UUID accountId, UUID templateId, CrawlerSelectors selectors, String reason, Instant at) {
        require(accountId, templateId);
        Integer version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM crawler_template_versions WHERE template_id = ?",
                Integer.class, templateId);
        insert(templateId, version, selectors, "NEEDS_REVIEW", reason, at);
        return findVersions(accountId, templateId).stream()
                .filter(item -> item.version() == version).findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public CrawlerTemplateVersion activate(UUID accountId, UUID templateId, int version, Instant at) {
        require(accountId, templateId);
        int changed = jdbc.update("""
                UPDATE crawler_templates SET active_version = ? WHERE id = ? AND account_id = ?
                AND EXISTS (SELECT 1 FROM crawler_template_versions WHERE template_id = ? AND version = ?)
                """, version, templateId, accountId, templateId, version);
        if (changed != 1) throw new IllegalArgumentException("Crawler template version was not found");
        jdbc.update("UPDATE crawler_template_versions SET status = CASE WHEN version = ? THEN 'ACTIVE' ELSE 'HISTORICAL' END WHERE template_id = ?",
                version, templateId);
        return findCurrent(accountId, templateId).orElseThrow();
    }

    private void require(UUID accountId, UUID templateId) {
        if (jdbc.queryForObject(
                "SELECT count(*) FROM crawler_templates WHERE id = ? AND account_id = ?",
                Integer.class, templateId, accountId) != 1) {
            throw new IllegalArgumentException("Crawler template was not found: " + templateId);
        }
    }

    private void insert(
            UUID id, int version, CrawlerSelectors selectors, String status, String reason, Instant at) {
        jdbc.update("""
                INSERT INTO crawler_template_versions (
                    template_id, version, item_selector, title_selector, link_selector,
                    date_selector, status, repair_reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, version, selectors.itemSelector(), selectors.titleSelector(),
                selectors.linkSelector(), selectors.dateSelector(), status, reason, utc(at));
    }

    private CrawlerTemplateVersion row(ResultSet rs, int ignored) throws SQLException {
        return new CrawlerTemplateVersion(
                rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("site_url"), rs.getInt("version"),
                new CrawlerSelectors(rs.getString("item_selector"), rs.getString("title_selector"),
                        rs.getString("link_selector"), rs.getString("date_selector")),
                rs.getString("status"), rs.getString("repair_reason"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
