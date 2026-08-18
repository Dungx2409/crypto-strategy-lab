package com.cryptolab.api.shared;

import com.cryptolab.marketdata.domain.MarketDataHealthStatus;
import com.cryptolab.news.domain.NewsHealthSnapshot;
import com.cryptolab.shared.domain.OperationalStatusSnapshot;

public record SystemStatusResponse(
        MarketDataHealthStatus marketData,
        String news,
        String sentiment,
        String queue,
        long queueDepth,
        int workerConsumers,
        long runningJobs,
        long pendingOutboxEvents) {

    static SystemStatusResponse from(
            MarketDataHealthStatus market,
            NewsHealthSnapshot news,
            OperationalStatusSnapshot operations) {
        return new SystemStatusResponse(
                market,
                news.providerStatus().name(),
                news.sentimentStatus().name(),
                operations.brokerAvailable() ? "UP" : "DOWN",
                operations.queueDepth(),
                operations.workerConsumers(),
                operations.runningJobs(),
                operations.pendingOutboxEvents());
    }
}
