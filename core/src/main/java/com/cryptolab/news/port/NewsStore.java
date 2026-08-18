package com.cryptolab.news.port;

import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsInsight;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NewsStore {

    int saveNewsItems(List<NewsItem> items, Instant storedAt);

    boolean hasPrediction(
            String newsId,
            String inputVersion,
            ModelDescriptor model,
            String preprocessingVersion);

    void saveSentiment(SentimentResult result);

    List<NewsInsight> findLatest(int limit);

    Optional<Instant> latestPublishedAt();
}
