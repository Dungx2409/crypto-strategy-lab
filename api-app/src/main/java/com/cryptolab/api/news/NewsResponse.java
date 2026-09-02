package com.cryptolab.api.news;

import com.cryptolab.news.domain.NewsHealthSnapshot;
import java.time.Instant;
import java.util.List;

public record NewsResponse(
        String provider,
        String providerStatus,
        String sentimentStatus,
        Instant lastCollectionAt,
        String lastError,
        List<NewsItemResponse> items) {

    static NewsResponse from(
            String provider, NewsHealthSnapshot health, List<NewsItemResponse> items) {
        return new NewsResponse(
                provider,
                health.providerStatus().name(),
                health.sentimentStatus().name(),
                health.lastCollectionAt(),
                health.lastError(),
                List.copyOf(items));
    }
}
