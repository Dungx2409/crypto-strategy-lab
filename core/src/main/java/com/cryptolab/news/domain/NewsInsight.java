package com.cryptolab.news.domain;

import java.util.Objects;
import java.util.Optional;

public record NewsInsight(NewsItem item, Optional<SentimentResult> sentiment) {

    public NewsInsight {
        Objects.requireNonNull(item, "item must not be null");
        sentiment = Objects.requireNonNull(sentiment, "sentiment must not be null");
        sentiment.ifPresent(result -> {
            if (!item.newsId().equals(result.newsId())) {
                throw new IllegalArgumentException("sentiment must belong to the news item");
            }
        });
    }
}
