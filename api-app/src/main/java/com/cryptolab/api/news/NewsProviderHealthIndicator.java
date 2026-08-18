package com.cryptolab.api.news;

import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.domain.NewsHealthStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("newsProviderHealthIndicator")
final class NewsProviderHealthIndicator implements HealthIndicator {

    private final NewsCollector collector;

    NewsProviderHealthIndicator(NewsCollector collector) {
        this.collector = collector;
    }

    @Override
    public Health health() {
        var snapshot = collector.health();
        Health.Builder builder = health(snapshot.providerStatus());
        builder.withDetail("status", snapshot.providerStatus());
        if (snapshot.lastCollectionAt() != null) {
            builder.withDetail("lastCollectionAt", snapshot.lastCollectionAt());
        }
        if (snapshot.lastError() != null) {
            builder.withDetail("lastError", snapshot.lastError());
        }
        return builder.build();
    }

    private static Health.Builder health(NewsHealthStatus status) {
        return switch (status) {
            case UP -> Health.up();
            case DEGRADED -> Health.status("DEGRADED");
            case DOWN -> Health.down();
        };
    }
}
