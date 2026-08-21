package com.cryptolab.api.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MarketDashboardIsolationTest {

    @Test
    void timeframeChangeOnlyReloadsTheSelectedChartSubscriptionAndData() throws IOException {
        String javascript;
        try (var input = getClass().getResourceAsStream("/static/market.js")) {
            if (input == null) {
                throw new AssertionError("market.js is missing");
            }
            javascript = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(javascript)
                .contains("chartStates")
                .contains("state.timeframeSelect.addEventListener(\"change\", () => reloadChart(state.index))")
                .contains("unsubscribeChart(index);")
                .contains("/api/v1/market/candles")
                .contains("subscribeChart(index);")
                .contains("event.type !== \"CANDLE_UPDATE\"")
                .doesNotContain("location.reload")
                .doesNotContain("/api/v1/news")
                .doesNotContain("/api/v1/leaderboard")
                .doesNotContain("/api/v1/search");
    }
}
