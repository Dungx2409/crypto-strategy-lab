package com.cryptolab.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsHealthStatus;
import com.cryptolab.news.domain.NewsInsight;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.NewsProvider;
import com.cryptolab.news.port.NewsStore;
import com.cryptolab.news.port.NewsTelemetry;
import com.cryptolab.news.port.SentimentAnalyzer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NewsCollectorTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void storesNormalizedNewsBeforeVersionedSentiment() {
        InMemoryStore store = new InMemoryStore();
        CountingTelemetry telemetry = new CountingTelemetry();
        NewsCollector collector = collector(since -> List.of(item("news-1")), new StubAnalyzer(), store, telemetry);

        var result = collector.collect();

        assertThat(result.providerStatus()).isEqualTo(NewsHealthStatus.UP);
        assertThat(result.sentimentStatus()).isEqualTo(NewsHealthStatus.UP);
        assertThat(result.stored()).isOne();
        assertThat(result.analyzed()).isOne();
        assertThat(store.latest).singleElement().satisfies(insight -> {
            assertThat(insight.item().newsId()).isEqualTo("news-1");
            assertThat(insight.sentiment()).get().extracting(SentimentResult::model)
                    .isEqualTo(StubAnalyzer.MODEL);
        });
        assertThat(telemetry.completed).hasValue(1);
    }

    @Test
    void providerFailureIsContainedAndPreviouslyStoredNewsRemainsReadable() {
        InMemoryStore store = new InMemoryStore();
        store.saveNewsItems(List.of(item("stored-news")), NOW.minusSeconds(60));
        CountingTelemetry telemetry = new CountingTelemetry();
        NewsProvider unavailable = since -> {
            throw new IllegalStateException("provider offline");
        };
        NewsCollector collector = collector(unavailable, new StubAnalyzer(), store, telemetry);

        var result = collector.collect();

        assertThat(result.providerStatus()).isEqualTo(NewsHealthStatus.DOWN);
        assertThat(result.message()).isEqualTo("provider offline");
        assertThat(collector.latest(10)).extracting(insight -> insight.item().newsId())
                .containsExactly("stored-news");
        assertThat(telemetry.collectionFailures).hasValue(1);
    }

    @Test
    void collectRecentRefetchesLookbackWindowEvenWhenStoreAlreadyHasHeadlines() {
        InMemoryStore store = new InMemoryStore();
        NewsItem existing = item("news-recent");
        store.saveNewsItems(List.of(existing), NOW.minusSeconds(10));
        store.saveSentiment(new SentimentResult(
                existing.newsId(),
                SentimentLabel.POSITIVE,
                BigDecimal.ONE,
                StubAnalyzer.MODEL,
                existing.inputVersion(),
                "prep-v1",
                NOW.minusSeconds(10)));
        CountingTelemetry telemetry = new CountingTelemetry();
        NewsProvider provider = since -> existing.publishedAt().isAfter(since) ? List.of(existing) : List.of();
        NewsCollector collector = collector(provider, new StubAnalyzer(), store, telemetry);

        var incremental = collector.collect();
        var recent = collector.collectRecent();

        assertThat(incremental.fetched()).isZero();
        assertThat(recent.fetched()).isOne();
        assertThat(recent.analyzed()).isZero();
        assertThat(recent.message()).contains("already stored and scored");
    }

    @Test
    void analyzePendingScoresStoredArticlesWithoutSentiment() {
        InMemoryStore store = new InMemoryStore();
        store.saveNewsItems(List.of(item("pending-1"), item("pending-2")), NOW.minusSeconds(30));
        CountingTelemetry telemetry = new CountingTelemetry();
        NewsCollector collector = collector(since -> List.of(), new StubAnalyzer(), store, telemetry);

        var result = collector.analyzePending(20);

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.analyzed()).isEqualTo(2);
        assertThat(result.inferenceFailures()).isZero();
        assertThat(store.latest).allSatisfy(insight -> assertThat(insight.sentiment()).isPresent());
        assertThat(telemetry.completed).hasValue(2);
    }

    @Test
    void sentimentFailureRetriesTwiceButDoesNotRollbackCollectedNews() {
        InMemoryStore store = new InMemoryStore();
        CountingTelemetry telemetry = new CountingTelemetry();
        StubAnalyzer analyzer = new StubAnalyzer();
        analyzer.failure = new IllegalStateException("model offline");
        NewsCollector collector = collector(since -> List.of(item("news-2")), analyzer, store, telemetry);

        var result = collector.collect();

        assertThat(analyzer.attempts).hasValue(2);
        assertThat(result.providerStatus()).isEqualTo(NewsHealthStatus.UP);
        assertThat(result.sentimentStatus()).isEqualTo(NewsHealthStatus.DOWN);
        assertThat(result.inferenceFailures()).isOne();
        assertThat(store.latest).singleElement().satisfies(insight -> {
            assertThat(insight.item().newsId()).isEqualTo("news-2");
            assertThat(insight.sentiment()).isEmpty();
        });
        assertThat(telemetry.inferenceFailures).hasValue(1);
    }

    private static NewsCollector collector(
            NewsProvider provider,
            SentimentAnalyzer analyzer,
            NewsStore store,
            NewsTelemetry telemetry) {
        return new NewsCollector(
                provider, analyzer, store, telemetry, CLOCK, Duration.ofHours(24), 2);
    }

    private static NewsItem item(String id) {
        return new NewsItem(
                id,
                "TestFeed",
                "Bitcoin adoption gains",
                "https://example.test/" + id,
                NOW.minusSeconds(30),
                "Bitcoin adoption gains",
                "input-v1");
    }

    private static final class StubAnalyzer implements SentimentAnalyzer {
        private static final ModelDescriptor MODEL = new ModelDescriptor("stub", "v1");
        private final AtomicInteger attempts = new AtomicInteger();
        private RuntimeException failure;

        @Override
        public ModelDescriptor descriptor() {
            return MODEL;
        }

        @Override
        public String preprocessingVersion() {
            return "prep-v1";
        }

        @Override
        public SentimentResult analyze(NewsItem item) {
            attempts.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return new SentimentResult(
                    item.newsId(), SentimentLabel.POSITIVE, BigDecimal.ONE,
                    MODEL, item.inputVersion(), preprocessingVersion(), NOW);
        }
    }

    private static final class CountingTelemetry implements NewsTelemetry {
        private final AtomicInteger collectionFailures = new AtomicInteger();
        private final AtomicInteger inferenceFailures = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();

        @Override
        public void collectionFailed(Throwable failure) {
            collectionFailures.incrementAndGet();
        }

        @Override
        public void inferenceFailed(String newsId, int attempts, Throwable failure) {
            inferenceFailures.incrementAndGet();
        }

        @Override
        public void inferenceCompleted(Duration duration) {
            completed.incrementAndGet();
        }
    }

    private static final class InMemoryStore implements NewsStore {
        private final List<NewsInsight> latest = new ArrayList<>();

        @Override
        public int saveNewsItems(List<NewsItem> items, Instant storedAt) {
            for (NewsItem item : items) {
                Optional<SentimentResult> previous = latest.stream()
                        .filter(existing -> existing.item().newsId().equals(item.newsId()))
                        .flatMap(existing -> existing.sentiment().stream())
                        .findFirst();
                latest.removeIf(existing -> existing.item().newsId().equals(item.newsId()));
                latest.add(new NewsInsight(item, previous));
            }
            return items.size();
        }

        @Override
        public boolean hasPrediction(
                String newsId,
                String inputVersion,
                ModelDescriptor model,
                String preprocessingVersion) {
            return latest.stream()
                    .filter(insight -> insight.item().newsId().equals(newsId))
                    .flatMap(insight -> insight.sentiment().stream())
                    .anyMatch(result -> result.model().equals(model)
                            && result.inputVersion().equals(inputVersion)
                            && result.preprocessingVersion().equals(preprocessingVersion));
        }

        @Override
        public void saveSentiment(SentimentResult result) {
            for (int index = 0; index < latest.size(); index++) {
                NewsInsight insight = latest.get(index);
                if (insight.item().newsId().equals(result.newsId())) {
                    latest.set(index, new NewsInsight(insight.item(), Optional.of(result)));
                    return;
                }
            }
            throw new IllegalStateException("news item missing");
        }

        @Override
        public List<NewsInsight> findLatest(int limit) {
            return latest.stream()
                    .sorted(Comparator.comparing((NewsInsight insight) -> insight.item().publishedAt()).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Instant> latestPublishedAt() {
            return latest.stream().map(insight -> insight.item().publishedAt()).max(Comparator.naturalOrder());
        }
    }
}
