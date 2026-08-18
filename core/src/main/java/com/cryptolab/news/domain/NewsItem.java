package com.cryptolab.news.domain;

import java.time.Instant;
import java.util.Objects;

public record NewsItem(
        String newsId,
        String provider,
        String title,
        String url,
        Instant publishedAt,
        String normalizedText,
        String inputVersion) {

    public NewsItem {
        newsId = requireText(newsId, "newsId");
        provider = requireText(provider, "provider");
        title = requireText(title, "title");
        url = requireText(url, "url");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        normalizedText = requireText(normalizedText, "normalizedText");
        inputVersion = requireText(inputVersion, "inputVersion");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
