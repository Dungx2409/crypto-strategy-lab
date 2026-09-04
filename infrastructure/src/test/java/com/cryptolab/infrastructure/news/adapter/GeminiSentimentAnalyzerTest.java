package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GeminiSentimentAnalyzerTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final NewsItem ITEM = new NewsItem(
            "news-1", "source", "Bitcoin adoption expands", "https://example.com/1",
            NOW.minusSeconds(60), "Bitcoin adoption expands to another country", "input-v1");

    @Test
    void parsesVersionedSemanticSentiment() {
        var analyzer = new GeminiSentimentAnalyzer(
                ignored -> "{\"label\":\"POSITIVE\",\"score\":0.82,\"summary\":\"Adoption news looks constructive for BTC near term.\"}",
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), "gemini-test");

        var result = analyzer.analyze(ITEM);

        assertThat(result.sentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(result.score()).isEqualByComparingTo("0.82");
        assertThat(result.model().version()).isEqualTo("gemini-test");
        assertThat(result.summary()).contains("Adoption news");
    }

    @Test
    void rejectsInconsistentModelOutput() {
        var analyzer = new GeminiSentimentAnalyzer(
                ignored -> "{\"label\":\"NEGATIVE\",\"score\":0.8}",
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), "gemini-test");

        assertThatThrownBy(() -> analyzer.analyze(ITEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
    }
}
