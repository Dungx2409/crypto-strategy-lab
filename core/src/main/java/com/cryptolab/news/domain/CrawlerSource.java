package com.cryptolab.news.domain;

import java.time.Instant;
import java.util.UUID;

public record CrawlerSource(
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
        Instant createdAt,
        Instant updatedAt) {

    public CrawlerSource {
        if (id == null) throw new IllegalArgumentException("source id must not be null");
        name = required(name, "name");
        listUrl = required(listUrl, "listUrl");
        articleSelector = required(articleSelector, "articleSelector");
        titleSelector = required(titleSelector, "titleSelector");
        linkSelector = required(linkSelector, "linkSelector");
        contentSelector = required(contentSelector, "contentSelector");
        publishedAtSelector = required(publishedAtSelector, "publishedAtSelector");
        relatedCoinsSelector = relatedCoinsSelector == null ? "" : relatedCoinsSelector.trim();
        if (version < 1) throw new IllegalArgumentException("source version must be positive");
        if (consecutiveFailures < 0) throw new IllegalArgumentException("failure count must not be negative");
        if (createdAt == null || updatedAt == null) throw new IllegalArgumentException("source timestamps are required");
    }

    public boolean degraded() {
        return consecutiveFailures >= 3;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
