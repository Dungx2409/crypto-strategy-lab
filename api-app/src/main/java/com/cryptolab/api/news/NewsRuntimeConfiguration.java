package com.cryptolab.api.news;

import com.cryptolab.infrastructure.news.adapter.DeterministicKeywordSentimentAnalyzer;
import com.cryptolab.infrastructure.news.adapter.GeminiSentimentAnalyzer;
import com.cryptolab.infrastructure.news.adapter.RssNewsProvider;
import com.cryptolab.infrastructure.news.adapter.SelectableNewsProvider;
import com.cryptolab.infrastructure.strategy.adapter.GeminiStrategyAuthoringModel;
import com.cryptolab.infrastructure.news.adapter.cryptocompare.CryptoCompareNewsProvider;
import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.application.CrawlerTemplateService;
import com.cryptolab.news.application.CrawlerNewsCollectionService;
import com.cryptolab.news.port.CrawlerArticleExtractor;
import com.cryptolab.news.port.CrawlerTemplateRepository;
import com.cryptolab.news.port.CrawlerSelectorRepairModel;
import com.cryptolab.news.port.CrawlerPageReader;
import com.cryptolab.news.port.CrawlerSelectorMatcher;
import com.cryptolab.news.port.NewsFeedPreferences;
import com.cryptolab.news.port.NewsProvider;
import com.cryptolab.news.port.NewsStore;
import com.cryptolab.news.port.NewsTelemetry;
import com.cryptolab.news.port.SentimentAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
class NewsRuntimeConfiguration {

    @Bean
    CrawlerTemplateService crawlerTemplateService(
            CrawlerTemplateRepository repository,
            CrawlerSelectorRepairModel repairModel,
            CrawlerPageReader pageReader,
            CrawlerSelectorMatcher matcher,
            Clock marketDataClock) {
        return new CrawlerTemplateService(
                repository, repairModel, marketDataClock, UUID::randomUUID, pageReader, matcher);
    }

    @Bean
    CrawlerNewsCollectionService crawlerNewsCollectionService(
            CrawlerTemplateRepository repository,
            CrawlerPageReader pageReader,
            CrawlerArticleExtractor extractor,
            NewsCollector collector,
            Clock marketDataClock) {
        return new CrawlerNewsCollectionService(
                repository, pageReader, extractor, collector, marketDataClock);
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService newsCollectionExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "news-collection");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "shutdown")
    TaskScheduler newsCollectionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("news-schedule-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    NewsProvider newsProvider(
            ObjectMapper objectMapper,
            NewsFeedPreferences preferences,
            @Value("${crypto.news.cryptocompare.url:https://min-api.cryptocompare.com/data/v2/news/}")
                    URI endpoint,
            @Value("${crypto.news.cryptocompare.api-key:}") String apiKey,
            @Value("${crypto.news.rss.urls:}") String rssUrls,
            @Value("${crypto.news.connect-timeout:5s}") Duration connectTimeout,
            @Value("${crypto.news.request-timeout:10s}") Duration requestTimeout,
            @Value("${crypto.news.maximum-items:50}") int maximumItems) {
        NewsProvider cryptoCompare = new CryptoCompareNewsProvider(
                objectMapper, endpoint, apiKey, connectTimeout, requestTimeout, maximumItems);
        List<URI> feeds = rssFeeds(rssUrls);
        NewsProvider rss = new RssNewsProvider(
                feeds, connectTimeout, requestTimeout, maximumItems);
        return new SelectableNewsProvider(
                preferences, cryptoCompare, rss, maximumItems);
    }

    @Bean
    @ConditionalOnProperty(
            name = "crypto.sentiment.provider",
            havingValue = "keyword",
            matchIfMissing = true)
    SentimentAnalyzer deterministicKeywordSentimentAnalyzer(Clock marketDataClock) {
        return new DeterministicKeywordSentimentAnalyzer(marketDataClock);
    }

    @Bean
    @ConditionalOnProperty(name = "crypto.sentiment.provider", havingValue = "gemini")
    SentimentAnalyzer geminiSentimentAnalyzer(
            GeminiStrategyAuthoringModel gemini,
            ObjectMapper objectMapper,
            Clock marketDataClock,
            @Value("${crypto.ai.gemini.model:gemini-2.5-flash}") String model) {
        return new GeminiSentimentAnalyzer(gemini, objectMapper, marketDataClock, model);
    }

    @Bean
    NewsCollector newsCollector(
            NewsProvider provider,
            SentimentAnalyzer analyzer,
            NewsStore store,
            NewsTelemetry telemetry,
            NewsFeedPreferences preferences,
            Clock marketDataClock,
            @Value("${crypto.news.initial-lookback:24h}") Duration initialLookback,
            @Value("${crypto.sentiment.max-attempts:2}") int maximumInferenceAttempts) {
        return new NewsCollector(
                provider,
                analyzer,
                store,
                telemetry,
                preferences,
                marketDataClock,
                initialLookback,
                maximumInferenceAttempts);
    }

    private static List<URI> rssFeeds(String csv) {
        List<URI> feeds = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(URI::create)
                .toList();
        if (feeds.isEmpty()) {
            throw new IllegalArgumentException(
                    "NEWS_RSS_URLS must contain at least one feed for RSS or composite mode");
        }
        return feeds;
    }
}
