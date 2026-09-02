package com.cryptolab.api.news;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.infrastructure.news.adapter.DeterministicKeywordSentimentAnalyzer;
import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsInsight;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.NewsProvider;
import com.cryptolab.news.port.NewsStore;
import com.cryptolab.news.port.NewsTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NewsControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private MutableProvider provider;
    private MockMvc mockMvc;
    private ThreadPoolTaskScheduler taskScheduler;
    private java.util.concurrent.ExecutorService executor;

    @BeforeEach
    void setUp() {
        provider = new MutableProvider();
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();
        NewsCollector collector = new NewsCollector(
                provider,
                new DeterministicKeywordSentimentAnalyzer(CLOCK),
                new InMemoryStore(),
                NewsTelemetry.noop(),
                preferences,
                CLOCK,
                Duration.ofHours(24),
                2);
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();
        executor = Executors.newSingleThreadExecutor();
        NewsCollectionScheduler scheduler = new NewsCollectionScheduler(
                collector, preferences, taskScheduler, executor);
        mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(collector, preferences, scheduler))
                .setControllerAdvice(new NewsExceptionHandler(CLOCK))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper()
                                .findAndRegisterModules()
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void collectsAndReturnsHeadlineScoreAndHonestModelVersion() throws Exception {
        provider.items = List.of(item());

        mockMvc.perform(post("/api/v1/news/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerStatus").value("UP"))
                .andExpect(jsonPath("$.sentimentStatus").value("UP"))
                .andExpect(jsonPath("$.analyzed").value(1));

        mockMvc.perform(get("/api/v1/news").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Bitcoin adoption gains"))
                .andExpect(jsonPath("$.items[0].sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.items[0].score").value(1.0))
                .andExpect(jsonPath("$.items[0].modelName").value("deterministic-keyword"))
                .andExpect(jsonPath("$.items[0].modelVersion").value("keyword-v1"));
    }

    @Test
    void reportsProviderDownAsDataWithoutTurningItIntoACascadingHttpFailure() throws Exception {
        provider.failure = new IllegalStateException("provider offline");

        mockMvc.perform(post("/api/v1/news/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerStatus").value("DOWN"))
                .andExpect(jsonPath("$.message").value("provider offline"));
    }

    @Test
    void updatesCoinAndIntervalPreferences() throws Exception {
        mockMvc.perform(put("/api/v1/news/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interval\":\"1m\",\"coin\":\"ETHUSDT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("1m"))
                .andExpect(jsonPath("$.coin").value("ETH"))
                .andExpect(jsonPath("$.categories").value("ETH"));

        mockMvc.perform(get("/api/v1/news/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("1m"))
                .andExpect(jsonPath("$.coin").value("ETH"));
    }

    @Test
    void collectUsesConfiguredCoinCategories() throws Exception {
        provider.items = List.of(item());

        mockMvc.perform(put("/api/v1/news/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interval\":\"5m\",\"coin\":\"BTC\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/news/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzed").value(1));

        org.assertj.core.api.Assertions.assertThat(provider.lastCategories).isEqualTo("BTC");
    }

    private static NewsItem item() {
        return new NewsItem(
                "news-1", "Example Feed", "Bitcoin adoption gains",
                "https://example.test/news-1", NOW.minusSeconds(60),
                "Bitcoin adoption gains and record growth", "input-v1");
    }

    private static final class MutableProvider implements NewsProvider {
        private List<NewsItem> items = List.of();
        private RuntimeException failure;
        private String lastCategories = "";

        @Override
        public List<NewsItem> fetchSince(Instant since) {
            return fetchSince(since, "");
        }

        @Override
        public List<NewsItem> fetchSince(Instant since, String categoriesCsv) {
            lastCategories = categoriesCsv == null ? "" : categoriesCsv;
            if (failure != null) {
                throw failure;
            }
            return items;
        }
    }

    private static final class InMemoryStore implements NewsStore {
        private final List<NewsInsight> insights = new ArrayList<>();

        @Override
        public int saveNewsItems(List<NewsItem> items, Instant storedAt) {
            for (NewsItem item : items) {
                insights.removeIf(existing -> existing.item().newsId().equals(item.newsId()));
                insights.add(new NewsInsight(item, Optional.empty()));
            }
            return items.size();
        }

        @Override
        public boolean hasPrediction(
                String newsId,
                String inputVersion,
                ModelDescriptor model,
                String preprocessingVersion) {
            return false;
        }

        @Override
        public void saveSentiment(SentimentResult result) {
            for (int index = 0; index < insights.size(); index++) {
                if (insights.get(index).item().newsId().equals(result.newsId())) {
                    insights.set(index, new NewsInsight(insights.get(index).item(), Optional.of(result)));
                }
            }
        }

        @Override
        public List<NewsInsight> findLatest(int limit) {
            return insights.stream()
                    .sorted(Comparator.comparing((NewsInsight insight) -> insight.item().publishedAt()).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Instant> latestPublishedAt() {
            return insights.stream().map(insight -> insight.item().publishedAt()).max(Comparator.naturalOrder());
        }
    }
}
