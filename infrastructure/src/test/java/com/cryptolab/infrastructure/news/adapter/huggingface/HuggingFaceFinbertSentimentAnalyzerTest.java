package com.cryptolab.infrastructure.news.adapter.huggingface;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HuggingFaceFinbertSentimentAnalyzerTest {

    @Test
    void convertsFinbertProbabilitiesToSignedScoreAndKeepsModelIdentity() {
        var analyzer = new HuggingFaceFinbertSentimentAnalyzer(
                (endpoint, token, body) -> """
                        [{"label":"positive","score":0.8},
                         {"label":"neutral","score":0.1},
                         {"label":"negative","score":0.1}]
                        """,
                new ObjectMapper(),
                URI.create("https://example.test/models/finbert"),
                "token",
                "ProsusAI/finbert",
                "4556d13015211d73dccd3fdd39d39232506f3e43",
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC));

        var result = analyzer.analyze(new NewsItem(
                "news-1", "test", "Markets rally", "https://example.test/1",
                Instant.parse("2026-08-23T00:00:00Z"), "Markets rally", "input-v1"));

        assertThat(result.sentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(result.score()).isEqualByComparingTo("0.7");
        assertThat(result.model().name()).isEqualTo("ProsusAI/finbert");
        assertThat(result.model().version())
                .isEqualTo("4556d13015211d73dccd3fdd39d39232506f3e43");
    }
}
