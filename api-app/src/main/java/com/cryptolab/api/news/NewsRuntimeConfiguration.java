package com.cryptolab.api.news;

import com.cryptolab.infrastructure.news.adapter.DeterministicKeywordSentimentAnalyzer;
import com.cryptolab.infrastructure.news.adapter.cryptocompare.CryptoCompareNewsProvider;
import com.cryptolab.infrastructure.news.adapter.html.ConfigurableHtmlNewsProvider;
import com.cryptolab.infrastructure.news.adapter.huggingface.HuggingFaceFinbertSentimentAnalyzer;
import com.cryptolab.news.application.CrawlerSourceService;
import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.port.NewsProvider;
import com.cryptolab.news.port.CrawlerSourceRepository;
import com.cryptolab.news.port.NewsStore;
import com.cryptolab.news.port.NewsTelemetry;
import com.cryptolab.news.port.SentimentAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NewsRuntimeConfiguration {

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
            havingValue = "cryptocompare")
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
            name = "crypto.news.provider",
            havingValue = "html",
            matchIfMissing = true)
    NewsProvider configurableHtmlNewsProvider(
            CrawlerSourceRepository sources,
            Clock marketDataClock,
            @Value("${crypto.news.allowed-hosts:}") String allowedHosts,
            @Value("${crypto.news.connect-timeout:5s}") Duration connectTimeout,
            @Value("${crypto.news.request-timeout:10s}") Duration requestTimeout,
            @Value("${crypto.news.maximum-items:50}") int maximumItems) {
        return new ConfigurableHtmlNewsProvider(
                sources, hosts(allowedHosts), connectTimeout, requestTimeout,
                marketDataClock, maximumItems);
    }

    @Bean
    CrawlerSourceService crawlerSourceService(
            CrawlerSourceRepository sources,
            Clock marketDataClock,
            @Value("${crypto.news.allowed-hosts:}") String allowedHosts) {
        return new CrawlerSourceService(
                sources, marketDataClock, UUID::randomUUID, hosts(allowedHosts));
    }

    @Bean
    @ConditionalOnProperty(
            name = "crypto.sentiment.provider",
            havingValue = "keyword")
    SentimentAnalyzer deterministicKeywordSentimentAnalyzer(Clock marketDataClock) {
        return new DeterministicKeywordSentimentAnalyzer(marketDataClock);
    }

    @Bean
    @ConditionalOnProperty(
            name = "crypto.sentiment.provider",
            havingValue = "huggingface",
            matchIfMissing = true)
    SentimentAnalyzer huggingFaceFinbertSentimentAnalyzer(
            ObjectMapper objectMapper,
            Clock marketDataClock,
            @Value("${crypto.sentiment.huggingface.endpoint}") URI endpoint,
            @Value("${crypto.sentiment.huggingface.token:}") String token,
            @Value("${crypto.sentiment.huggingface.model:ProsusAI/finbert}") String model,
            @Value("${crypto.sentiment.huggingface.revision:4556d13015211d73dccd3fdd39d39232506f3e43}") String revision,
            @Value("${crypto.sentiment.huggingface.connect-timeout:5s}") Duration connectTimeout,
            @Value("${crypto.sentiment.huggingface.request-timeout:20s}") Duration requestTimeout) {
        return new HuggingFaceFinbertSentimentAnalyzer(
                objectMapper, endpoint, token, model, revision,
                connectTimeout, requestTimeout, marketDataClock);
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

    private static Set<String> hosts(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
