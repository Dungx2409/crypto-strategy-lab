package com.cryptolab.api.experiment;

import com.cryptolab.shared.domain.SentimentObservation;
import java.math.BigDecimal;
import java.time.Instant;

public record SentimentObservationRequest(
        String sourceId,
        Instant observedAt,
        BigDecimal score,
        String modelName,
        String modelVersion,
        String inputVersion,
        String preprocessingVersion) {

    SentimentObservation toDomain() {
        return new SentimentObservation(
                sourceId,
                observedAt,
                score,
                modelName,
                modelVersion,
                inputVersion,
                preprocessingVersion);
    }
}
