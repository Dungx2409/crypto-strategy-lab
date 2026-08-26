package com.cryptolab.api.news;

import com.cryptolab.infrastructure.news.adapter.DeterministicKeywordSentimentAnalyzer;
import com.cryptolab.infrastructure.news.adapter.GeminiSentimentAnalyzer;
import com.cryptolab.infrastructure.strategy.adapter.GeminiStrategyAuthoringModel;
import com.cryptolab.infrastructure.news.adapter.cryptocompare.CryptoCompareNewsProvider;
import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.application.CrawlerTemplateService;
import com.cryptolab.news.port.CrawlerTemplateRepository;
import com.cryptolab.news.port.CrawlerSelectorRepairModel;
import com.cryptolab.news.port.CrawlerPageReader;
import com.cryptolab.news.port.CrawlerSelectorMatcher;
import com.cryptolab.news.port.NewsProvider;
import com.cryptolab.news.port.NewsStore;
import com.cryptolab.news.port.NewsTelemetry;
import com.cryptolab.news.port.SentimentAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService newsCollectionExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "news-collection");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnProperty(
            name = "crypto.news.provider",
            havingValue = "cryptocompare",
            matchIfMissing = true)
    NewsProvider cryptoCompareNewsProvider(
            ObjectMapper objectMapper,
            @Value("${crypto.news.cryptocompare.url:https://min-api.cryptocompare.com/data/v2/news/}")
                    URI endpoint,
            @Value("${crypto.news.cryptocompare.api-key:}") String apiKey,
            @Value("${crypto.news.connect-timeout:5s}") Duration connectTimeout,
            @Value("${crypto.news.request-timeout:10s}") Duration requestTimeout,
            @Value("${crypto.news.maximum-items:50}") int maximumItems) {
        return new CryptoCompareNewsProvider(
                objectMapper, endpoint, apiKey, connectTimeout, requestTimeout, maximumItems);
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
            @Value("${crypto.ai.gemini.model:gemini-3.7-flash}") String model) {
        return new GeminiSentimentAnalyzer(gemini, objectMapper, marketDataClock, model);
    }

    @Bean
    NewsCollector newsCollector(
            NewsProvider provider,
            SentimentAnalyzer analyzer,
            NewsStore store,
            NewsTelemetry telemetry,
            Clock marketDataClock,
            @Value("${crypto.news.initial-lookback:24h}") Duration initialLookback,
            @Value("${crypto.sentiment.max-attempts:2}") int maximumInferenceAttempts) {
        return new NewsCollector(
                provider,
                analyzer,
                store,
                telemetry,
                marketDataClock,
                initialLookback,
                maximumInferenceAttempts);
    }
}
