package com.cryptolab.news.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.List;

public record NewsItem(
        String newsId,
        String provider,
        String title,
        String url,
        Instant publishedAt,
        String normalizedText,
        String inputVersion,
        String content,
        Instant crawledAt,
        List<String> relatedCoins) {

    public NewsItem(
            String newsId,
            String provider,
            String title,
            String url,
            Instant publishedAt,
            String normalizedText,
            String inputVersion) {
        this(
                newsId,
                provider,
                title,
                url,
                publishedAt,
                normalizedText,
                inputVersion,
                normalizedText,
                publishedAt,
                List.of());
    }

    public NewsItem {
        newsId = requireText(newsId, "newsId");
        provider = requireText(provider, "provider");
        title = requireText(title, "title");
        url = requireText(url, "url");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        normalizedText = requireText(normalizedText, "normalizedText");
        inputVersion = requireText(inputVersion, "inputVersion");
        content = requireText(content, "content");
        Objects.requireNonNull(crawledAt, "crawledAt must not be null");
        relatedCoins = List.copyOf(relatedCoins == null ? List.of() : relatedCoins);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
