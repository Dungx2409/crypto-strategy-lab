package com.cryptolab.api.news;

import com.cryptolab.news.domain.NewsCollectionResult;
import java.time.Instant;

public record NewsCollectionResponse(
        int fetched,
        int stored,
        int analyzed,
        int inferenceFailures,
        String providerStatus,
        String sentimentStatus,
        Instant completedAt,
        String message) {

    static NewsCollectionResponse from(NewsCollectionResult result) {
        return new NewsCollectionResponse(
                result.fetched(),
                result.stored(),
                result.analyzed(),
                result.inferenceFailures(),
                result.providerStatus().name(),
                result.sentimentStatus().name(),
                result.completedAt(),
                result.message());
    }
}
