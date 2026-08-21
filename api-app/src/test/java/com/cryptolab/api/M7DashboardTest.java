package com.cryptolab.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class M7DashboardTest {

    @Test
    void exposesEveryRequiredPanelAndUsesBackendContractsWithoutMockData() throws IOException {
        String html = resource("/static/index.html");
        String lab = resource("/static/lab.js");

        assertThat(html).contains(
                "System status",
                "Realtime Chart",
                "Strategy configuration",
                "Search run",
                "Leaderboard",
                "Top #1 / Experiment details",
                "News + Sentiment",
                "/app.js",
                "/lab.js");
        assertThat(lab).contains(
                "/api/v1/strategies",
                "/api/v1/datasets",
                "datasetChecksum: dataset.checksum",
                "/api/v1/search-runs",
                "/api/v1/leaderboard",
                "/api/v1/experiments/",
                "/provenance",
                "/api/v1/system/status",
                "/topic/search/",
                "/topic/leaderboard/");
        assertThat(lab.toLowerCase()).doesNotContain("mock");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("Missing resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
