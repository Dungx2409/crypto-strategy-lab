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
                "data-symbol-picker",
                "coin-icon",
                "https://www.cryptocompare.com/media/37746251/btc.png",
                "https://www.cryptocompare.com/media/37746238/eth.png",
                "https://www.cryptocompare.com/media/37747734/sol.png",
                "https://www.cryptocompare.com/media/37746880/bnb.png",
                "data-primary-timeframe=\"30m\"",
                "data-primary-timeframe=\"2h\"",
                "data-primary-timeframe=\"1d\"");
        assertThat(market).contains(
                "chartStates",
                "reloadChart",
                "unsubscribeChart",
                "initSymbolPicker",
                "formatPrice",
                "toFixed(2)",
                "/api/v1/market/candles",
                "/topic/market/")
                .doesNotContain("location.reload");
    }

    @Test
    void accountFeaturesExposeAuthoringSchedulesManualBacktestsAndCrawlerReview() throws IOException {
        String html = resource("/static/index.html");
        String features = resource("/static/account-features.js");

        assertThat(html).contains(
                "id=\"auth-gate\"",
                "id=\"auth-gate-form\"",
                "id=\"auth-gate-username\"",
                "id=\"auth-gate-password\"",
                "class=\"app-shell\" hidden",
                "account-name",
                "strategy-prompt",
                "article-url",
                "data-authoring-mode=\"article\"",
                "Generate from URL",
                "confirm-strategy",
                "save-strategy",
                "strategy-source-preview",
                "schedule-list",
                "manual-strategy",
                "manual-period",
                "backtest-risk-profile",
                "backtest-advanced",
                "manual-from",
                "manual-to",
                "manual-history-list",
                "manual-backtest-message",
                "manual-backtest-progress",
                "trade-filter-direction",
                "crawler-template-list",
                "Advanced HTML selectors",
                "search-size",
                "schedule-lookback-preset",
                "schedule-frequency-preset",
                "account-features.js");
        assertThat(html)
                .contains("id=\"auth-credentials\"", "id=\"logout-account\"")
                .doesNotContain("Sessions use an HTTP-only cookie.");
        assertThat(features).contains(
                "/api/v1/auth/",
                "byId(\"auth-gate\").hidden = Boolean(account)",
                "byId(\"app-shell\").hidden = !account",
                "auth-credentials",
                "/api/v1/user-strategies",
                "/build",
                "/confirm",
                "/api/v1/discovery-schedules",
                "/api/v1/experiments",
                "/api/v1/crawler-templates",
                "query.set(\"from\"",
                "query.set(\"to\"",
                "setManualBacktestProgress",
                "applyBacktestPeriod",
                "applyRiskProfile",
                "Confirm");
    }

    @Test
    void discoveryExposesEveryAutomaticStopCondition() throws IOException {
        String html = resource("/static/index.html");
        String lab = resource("/static/lab.js");

        assertThat(html).contains(
                "max-candidates",
                "max-duration-seconds",
                "no-improvement-iterations");
        assertThat(lab).contains(
                "selectedStopConditions",
                "maxDuration: maxDurationSeconds === null ? null : `PT${maxDurationSeconds}S`",
                "Configure at least one automatic stop condition");
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
