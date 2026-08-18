package com.cryptolab.api.news;

import com.cryptolab.news.domain.NewsHealthSnapshot;
import java.time.Instant;
import java.util.List;

public record NewsResponse(
        String providerStatus,
        String sentimentStatus,
        Instant lastCollectionAt,
        String lastError,
        List<NewsItemResponse> items) {

    static NewsResponse from(NewsHealthSnapshot health, List<NewsItemResponse> items) {
        return new NewsResponse(
                health.providerStatus().name(),
                health.sentimentStatus().name(),
                health.lastCollectionAt(),
                health.lastError(),
                List.copyOf(items));
    }
}
