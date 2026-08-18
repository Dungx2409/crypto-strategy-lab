package com.cryptolab.api.news;

import com.cryptolab.news.domain.NewsInsight;
import java.math.BigDecimal;
import java.time.Instant;

public record NewsItemResponse(
        String newsId,
        String provider,
        String title,
        String url,
        Instant publishedAt,
        String sentiment,
        BigDecimal score,
        String modelName,
        String modelVersion,
        String inputVersion,
        String preprocessingVersion) {

    static NewsItemResponse from(NewsInsight insight) {
        var item = insight.item();
        var prediction = insight.sentiment().orElse(null);
        return new NewsItemResponse(
                item.newsId(),
                item.provider(),
                item.title(),
                item.url(),
                item.publishedAt(),
                prediction == null ? null : prediction.sentiment().name(),
                prediction == null ? null : prediction.score(),
                prediction == null ? null : prediction.model().name(),
                prediction == null ? null : prediction.model().version(),
                item.inputVersion(),
                prediction == null ? null : prediction.preprocessingVersion());
    }
}
