package com.cryptolab.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReferenceDashboardTest {

    @Test
    void providesTheReferenceApplicationShellAndCoreMvpScreens() throws IOException {
        String html = resource("/static/index.html");

        assertThat(html).contains(
                "app-sidebar",
                "Crypto Strategy Lab",
                "data-view=\"realtime\"",
                "data-view=\"discovery\"",
                "data-view=\"backtest\"",
                "data-view=\"news\"",
                "view-realtime",
                "view-discovery",
                "view-backtest",
                "view-news",
                "Binance API + WebSocket");
    }

    @Test
    void realtimeViewHasFourIndependentChartSlots() throws IOException {
        String html = resource("/static/index.html");
        String market = resource("/static/market.js");

        assertThat(html).contains(
                "market-chart-grid",
                "market-chart-0",
                "market-chart-1",
                "market-chart-2",
                "market-chart-3",
                "ETHUSDT",
                "SOLUSDT",
                "BNBUSDT",
                "data-primary-timeframe=\"30m\"",
                "data-primary-timeframe=\"2h\"",
                "data-primary-timeframe=\"1d\"");
        assertThat(market).contains(
                "chartStates",
                "reloadChart",
                "unsubscribeChart",
                "/api/v1/market/candles",
                "/topic/market/")
                .doesNotContain("location.reload");
    }

    private static String resource(String path) throws IOException {
        try (var input = ReferenceDashboardTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError(path + " is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
