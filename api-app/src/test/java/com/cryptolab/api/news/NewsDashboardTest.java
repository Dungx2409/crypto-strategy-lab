package com.cryptolab.api.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NewsDashboardTest {

    @Test
    void displaysNewsSentimentStatusScoreAndModelWithoutCouplingToMarketReload() throws IOException {
        String html = resource("/static/index.html");
        String news = resource("/static/news.js");

        assertThat(html)
                .contains("News + Sentiment", "news-status", "news-list", "/news.js");
        assertThat(news)
                .contains("/api/v1/news/collect")
                .contains("/api/v1/news?limit=20")
                .contains("item.sentiment", "item.score", "item.modelVersion")
                .doesNotContain("/api/v1/market")
                .doesNotContain("location.reload");
    }

    private static String resource(String path) throws IOException {
        try (var input = NewsDashboardTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError(path + " is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
