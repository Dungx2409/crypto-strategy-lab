package com.cryptolab.news.port;

import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.domain.CrawlerTemplateVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrawlerTemplateRepository {
    CrawlerTemplateVersion create(UUID id, UUID accountId, String siteUrl, CrawlerSelectors selectors, Instant at);
    Optional<CrawlerTemplateVersion> findCurrent(UUID accountId, UUID templateId);
    List<CrawlerTemplateVersion> findCurrentByAccount(UUID accountId);
    List<CrawlerTemplateVersion> findAllCurrent();
    List<CrawlerTemplateVersion> findVersions(UUID accountId, UUID templateId);
    CrawlerTemplateVersion addRepair(
            UUID accountId, UUID templateId, CrawlerSelectors selectors, String reason, Instant at);
    CrawlerTemplateVersion activate(UUID accountId, UUID templateId, int version, Instant at);
}
