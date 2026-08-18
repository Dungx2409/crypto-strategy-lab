package com.cryptolab.news.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SentimentResult(
        String newsId,
        SentimentLabel sentiment,
        BigDecimal score,
        ModelDescriptor model,
        String inputVersion,
        String preprocessingVersion,
        Instant createdAt) {

    public SentimentResult {
        if (newsId == null || newsId.isBlank()) {
            throw new IllegalArgumentException("newsId must not be blank");
        }
        newsId = newsId.trim();
        Objects.requireNonNull(sentiment, "sentiment must not be null");
        Objects.requireNonNull(score, "score must not be null");
        if (score.compareTo(BigDecimal.ONE.negate()) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("score must be between -1 and 1");
        }
        Objects.requireNonNull(model, "model must not be null");
        if (inputVersion == null || inputVersion.isBlank()) {
            throw new IllegalArgumentException("inputVersion must not be blank");
        }
        inputVersion = inputVersion.trim();
        if (preprocessingVersion == null || preprocessingVersion.isBlank()) {
            throw new IllegalArgumentException("preprocessingVersion must not be blank");
        }
        preprocessingVersion = preprocessingVersion.trim();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
