package com.cryptolab.api.news;

import com.cryptolab.news.domain.CrawlerSource;
import java.time.Instant;
import java.util.UUID;

public record CrawlerSourceResponse(
        UUID id,
        String name,
        String listUrl,
        String articleSelector,
        String titleSelector,
        String linkSelector,
        String contentSelector,
        String publishedAtSelector,
        String relatedCoinsSelector,
        boolean enabled,
        int version,
        int consecutiveFailures,
        String lastError,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    static CrawlerSourceResponse from(CrawlerSource source) {
        return new CrawlerSourceResponse(
                source.id(),
                source.name(),
                source.listUrl(),
                source.articleSelector(),
                source.titleSelector(),
                source.linkSelector(),
                source.contentSelector(),
                source.publishedAtSelector(),
                source.relatedCoinsSelector(),
                source.enabled(),
                source.version(),
                source.consecutiveFailures(),
                source.lastError(),
                source.degraded() ? "DEGRADED" : "HEALTHY",
                source.createdAt(),
                source.updatedAt());
    }
}
