package com.cryptolab.shared.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SentimentObservation(
        String sourceId,
        Instant observedAt,
        BigDecimal score,
        String modelName,
        String modelVersion,
        String inputVersion,
        String preprocessingVersion) {

    public SentimentObservation {
        sourceId = requireText(sourceId, "sourceId");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(score, "score must not be null");
        if (score.compareTo(BigDecimal.ONE.negate()) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("score must be between -1 and 1");
        }
        modelName = requireText(modelName, "modelName");
        modelVersion = requireText(modelVersion, "modelVersion");
        inputVersion = requireText(inputVersion, "inputVersion");
        preprocessingVersion = requireText(preprocessingVersion, "preprocessingVersion");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
