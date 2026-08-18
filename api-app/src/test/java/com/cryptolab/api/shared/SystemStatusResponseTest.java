package com.cryptolab.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.MarketDataHealthStatus;
import com.cryptolab.news.domain.NewsHealthSnapshot;
import com.cryptolab.news.domain.NewsHealthStatus;
import com.cryptolab.shared.domain.OperationalStatusSnapshot;
import org.junit.jupiter.api.Test;

class SystemStatusResponseTest {

    @Test
    void keepsMarketNewsAndQueueHealthSeparate() {
        SystemStatusResponse response = SystemStatusResponse.from(
                MarketDataHealthStatus.UP,
                new NewsHealthSnapshot(NewsHealthStatus.DOWN, NewsHealthStatus.DEGRADED, null, "offline"),
                new OperationalStatusSnapshot(true, 12, 3, 2, 4));

        assertThat(response.marketData()).isEqualTo(MarketDataHealthStatus.UP);
        assertThat(response.news()).isEqualTo("DOWN");
        assertThat(response.sentiment()).isEqualTo("DEGRADED");
        assertThat(response.queue()).isEqualTo("UP");
        assertThat(response.queueDepth()).isEqualTo(12);
        assertThat(response.workerConsumers()).isEqualTo(3);
    }
}
