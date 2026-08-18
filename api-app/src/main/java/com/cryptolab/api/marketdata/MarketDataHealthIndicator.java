package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.application.MarketDataStreamService;
import com.cryptolab.marketdata.domain.MarketDataHealthStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("marketDataHealthIndicator")
final class MarketDataHealthIndicator implements HealthIndicator {

    private final MarketDataStreamService streamService;

    MarketDataHealthIndicator(MarketDataStreamService streamService) {
        this.streamService = streamService;
    }

    @Override
    public Health health() {
        MarketDataHealthStatus status = streamService.healthStatus();
        Health.Builder builder = switch (status) {
            case UP -> Health.up();
            case DEGRADED -> Health.status("DEGRADED");
            case DOWN -> Health.down();
        };
        return builder.withDetail("status", status)
                .withDetail("activeStreams", streamService.activeStreamCount())
                .build();
    }
}
