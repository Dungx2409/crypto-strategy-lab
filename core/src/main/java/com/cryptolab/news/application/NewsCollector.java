package com.cryptolab.news.application;

import com.cryptolab.news.domain.NewsCollectionResult;
import com.cryptolab.news.domain.NewsHealthSnapshot;
import com.cryptolab.news.domain.NewsHealthStatus;
import com.cryptolab.news.domain.NewsInsight;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.NewsFeedPreferences;
import com.cryptolab.news.port.NewsProvider;
import com.cryptolab.news.port.NewsStore;
import com.cryptolab.news.port.NewsTelemetry;
import com.cryptolab.news.port.SentimentAnalyzer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class NewsCollector {

    private final NewsProvider provider;
    private final SentimentAnalyzer analyzer;
    private final NewsStore store;
    private final NewsTelemetry telemetry;
    private final NewsFeedPreferences preferences;
    private final Clock clock;
    private final Duration initialLookback;
    private final int maximumInferenceAttempts;
    private volatile NewsHealthSnapshot health = new NewsHealthSnapshot(
            NewsHealthStatus.DOWN, NewsHealthStatus.UP, null, "news has not been collected yet");

    public NewsCollector(
            NewsProvider provider,
            SentimentAnalyzer analyzer,
            NewsStore store,
            NewsTelemetry telemetry,
            Clock clock,
            Duration initialLookback,
            int maximumInferenceAttempts) {
        this(
                provider,
                analyzer,
                store,
                telemetry,
                NewsFeedPreferences.allCoins(),
                clock,
                initialLookback,
                maximumInferenceAttempts);
    }

    public NewsCollector(
            NewsProvider provider,
            SentimentAnalyzer analyzer,
            NewsStore store,
            NewsTelemetry telemetry,
            NewsFeedPreferences preferences,
            Clock clock,
            Duration initialLookback,
            int maximumInferenceAttempts) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.initialLookback = Objects.requireNonNull(initialLookback, "initialLookback must not be null");
        if (initialLookback.isNegative() || initialLookback.isZero()) {
            throw new IllegalArgumentException("initialLookback must be positive");
        }
        if (maximumInferenceAttempts < 1 || maximumInferenceAttempts > 10) {
            throw new IllegalArgumentException("maximumInferenceAttempts must be between 1 and 10");
        }
        this.maximumInferenceAttempts = maximumInferenceAttempts;
    }

    public synchronized NewsCollectionResult collect() {
        Instant now = clock.instant();
        Instant since = store.latestPublishedAt().orElse(now.minus(initialLookback));
        return collectSince(since, now);
    }

    /**
     * Manual dashboard collect: always re-fetch the configured lookback window so a click
     * does not return zero merely because the store already has the newest headline.
     */
    public synchronized NewsCollectionResult collectRecent() {
        Instant now = clock.instant();
        return collectSince(now.minus(initialLookback), now);
    }

    public synchronized NewsCollectionResult collect(List<NewsItem> items) {
        return collect(distinct(items), clock.instant());
    }

    private NewsCollectionResult collectSince(Instant since, Instant now) {
        List<NewsItem> fetched;
        try {
            fetched = distinct(provider.fetchSince(since, preferences.categoriesCsv()));
        } catch (RuntimeException providerFailure) {
            telemetry.collectionFailed(providerFailure);
            health = new NewsHealthSnapshot(
                    NewsHealthStatus.DOWN,
                    health.sentimentStatus(),
                    now,
                    safeMessage(providerFailure));
            return new NewsCollectionResult(
                    0, 0, 0, 0,
                    health.providerStatus(),
                    health.sentimentStatus(),
                    now,
                    health.lastError());
        }
        return collect(fetched, now);
    }

    public synchronized NewsCollectionResult analyzePending(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        List<NewsItem> pending = store.findLatest(limit).stream()
                .filter(insight -> insight.sentiment().isEmpty())
                .map(NewsInsight::item)
                .toList();
        return collect(pending, clock.instant());
    }

    private NewsCollectionResult collect(List<NewsItem> fetched, Instant now) {
        int stored = store.saveNewsItems(fetched, now);
        int analyzed = 0;
        int inferenceFailures = 0;
        RuntimeException lastInferenceFailure = null;
        for (NewsItem item : fetched) {
            if (store.hasPrediction(
                    item.newsId(),
                    item.inputVersion(),
                    analyzer.descriptor(),
                    analyzer.preprocessingVersion())) {
                continue;
            }
            Instant inferenceStarted = clock.instant();
            try {
                SentimentResult result = analyzeWithBoundedRetry(item);
                validateResult(item, result);
                store.saveSentiment(result);
                analyzed++;
                telemetry.inferenceCompleted(Duration.between(inferenceStarted, clock.instant()));
            } catch (RuntimeException inferenceFailure) {
                inferenceFailures++;
                lastInferenceFailure = inferenceFailure;
                telemetry.inferenceFailed(item.newsId(), maximumInferenceAttempts, inferenceFailure);
            }
        }

        NewsHealthStatus sentimentStatus = sentimentStatus(fetched.size(), analyzed, inferenceFailures);
        String message = lastInferenceFailure == null ? null : safeMessage(lastInferenceFailure);
        if (message == null && fetched.isEmpty()) {
            message = "No articles found in the recent lookback window";
        } else if (message == null && analyzed == 0 && fetched.size() > 0) {
            message = "Articles were already stored and scored";
        }
        health = new NewsHealthSnapshot(NewsHealthStatus.UP, sentimentStatus, now, message);
        return new NewsCollectionResult(
                fetched.size(), stored, analyzed, inferenceFailures,
                NewsHealthStatus.UP, sentimentStatus, now, message);
    }

    public List<NewsInsight> latest(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return store.findLatest(limit);
    }

    public NewsHealthSnapshot health() {
        return health;
    }

    private SentimentResult analyzeWithBoundedRetry(NewsItem item) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maximumInferenceAttempts; attempt++) {
            try {
                return analyzer.analyze(item);
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        throw Objects.requireNonNull(lastFailure);
    }

    private NewsHealthStatus sentimentStatus(int fetched, int analyzed, int failures) {
        if (failures == 0) {
            return NewsHealthStatus.UP;
        }
        if (analyzed > 0 || fetched > failures) {
            return NewsHealthStatus.DEGRADED;
        }
        return NewsHealthStatus.DOWN;
    }

    private void validateResult(NewsItem item, SentimentResult result) {
        if (!item.newsId().equals(result.newsId())) {
            throw new IllegalArgumentException("sentiment result newsId does not match its input");
        }
        if (!item.inputVersion().equals(result.inputVersion())) {
            throw new IllegalArgumentException("sentiment result inputVersion does not match its input");
        }
        if (!analyzer.descriptor().equals(result.model())) {
            throw new IllegalArgumentException("sentiment result model metadata does not match its analyzer");
        }
        if (!analyzer.preprocessingVersion().equals(result.preprocessingVersion())) {
            throw new IllegalArgumentException("sentiment preprocessing version does not match its analyzer");
        }
    }

    private static List<NewsItem> distinct(List<NewsItem> items) {
        Objects.requireNonNull(items, "provider result must not be null");
        LinkedHashMap<String, NewsItem> distinct = new LinkedHashMap<>();
        for (NewsItem item : items) {
            NewsItem value = Objects.requireNonNull(item, "provider result must not contain null items");
            distinct.putIfAbsent(value.newsId(), value);
        }
        return List.copyOf(distinct.values());
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        String value = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
