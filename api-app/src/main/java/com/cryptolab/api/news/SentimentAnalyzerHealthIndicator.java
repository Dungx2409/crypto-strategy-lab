package com.cryptolab.api.news;

import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.domain.NewsHealthStatus;
import com.cryptolab.news.port.SentimentAnalyzer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("sentimentAnalyzerHealthIndicator")
final class SentimentAnalyzerHealthIndicator implements HealthIndicator {

    private final NewsCollector collector;
    private final SentimentAnalyzer analyzer;

    SentimentAnalyzerHealthIndicator(NewsCollector collector, SentimentAnalyzer analyzer) {
        this.collector = collector;
        this.analyzer = analyzer;
    }

    @Override
    public Health health() {
        NewsHealthStatus status = collector.health().sentimentStatus();
        Health.Builder builder = switch (status) {
            case UP -> Health.up();
            case DEGRADED -> Health.status("DEGRADED");
            case DOWN -> Health.down();
        };
        return builder.withDetail("status", status)
                .withDetail("model", analyzer.descriptor().name())
                .withDetail("modelVersion", analyzer.descriptor().version())
                .withDetail("preprocessingVersion", analyzer.preprocessingVersion())
                .build();
    }
}
