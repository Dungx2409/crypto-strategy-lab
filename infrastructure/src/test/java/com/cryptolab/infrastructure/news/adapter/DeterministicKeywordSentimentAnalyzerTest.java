package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeterministicKeywordSentimentAnalyzerTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private final DeterministicKeywordSentimentAnalyzer analyzer =
            new DeterministicKeywordSentimentAnalyzer(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void reportsHonestVersionedMetadataAndDeterministicScores() {
        var positive = analyzer.analyze(item("adoption growth rally"));
        var negative = analyzer.analyze(item("hack exploit losses"));
        var neutral = analyzer.analyze(item("protocol community meeting"));

        assertThat(positive.sentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(positive.score()).isEqualByComparingTo("1.0000");
        assertThat(negative.sentiment()).isEqualTo(SentimentLabel.NEGATIVE);
        assertThat(negative.score()).isEqualByComparingTo("-1.0000");
        assertThat(neutral.sentiment()).isEqualTo(SentimentLabel.NEUTRAL);
        assertThat(neutral.score()).isZero();
        assertThat(positive.model().name()).isEqualTo("deterministic-keyword");
        assertThat(positive.model().version()).isEqualTo("keyword-v1");
        assertThat(positive.preprocessingVersion()).isEqualTo("lowercase-token-v1");
        assertThat(positive.createdAt()).isEqualTo(NOW);
    }

    private static NewsItem item(String text) {
        return new NewsItem(
                "news-1", "feed", "title", "https://example.test/news-1",
                NOW.minusSeconds(1), text, "input-v1");
    }
}
