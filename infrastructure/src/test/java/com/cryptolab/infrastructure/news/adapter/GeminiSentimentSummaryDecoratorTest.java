package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.SentimentAnalyzer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeminiSentimentSummaryDecoratorTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final ModelDescriptor MODEL = new ModelDescriptor("finbert", "v1");
    private static final NewsItem ITEM = new NewsItem(
            "news-1", "source", "Bitcoin adoption expands", "https://example.com/1",
            NOW.minusSeconds(60), "Bitcoin adoption expands to another country", "input-v1");

    @Test
    void keepsDelegateScoreAndAddsGeminiParagraph() {
        var decorator = new GeminiSentimentSummaryDecorator(
                scored(SentimentLabel.POSITIVE, new BigDecimal("0.91"), null),
                prompt -> "Markets should read this as constructive for Bitcoin.");

        var result = decorator.analyze(ITEM);

        assertThat(result.sentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(result.score()).isEqualByComparingTo("0.91");
        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.summary()).contains("constructive for Bitcoin");
    }

    @Test
    void summaryFailureDoesNotDropScoredResult() {
        var decorator = new GeminiSentimentSummaryDecorator(
                scored(SentimentLabel.NEGATIVE, new BigDecimal("-0.4"), null),
                prompt -> {
                    throw new IllegalStateException("gemini down");
                });

        var result = decorator.analyze(ITEM);

        assertThat(result.sentiment()).isEqualTo(SentimentLabel.NEGATIVE);
        assertThat(result.summary()).isNull();
    }

    @Test
    void skipsGeminiWhenDelegateAlreadyProvidedSummary() {
        AtomicInteger calls = new AtomicInteger();
        var decorator = new GeminiSentimentSummaryDecorator(
                scored(SentimentLabel.NEUTRAL, BigDecimal.ZERO, "Already summarized."),
                prompt -> {
                    calls.incrementAndGet();
                    return "should not run";
                });

        var result = decorator.analyze(ITEM);

        assertThat(result.summary()).isEqualTo("Already summarized.");
        assertThat(calls).hasValue(0);
    }

    private static SentimentAnalyzer scored(SentimentLabel label, BigDecimal score, String summary) {
        return new SentimentAnalyzer() {
            @Override
            public ModelDescriptor descriptor() {
                return MODEL;
            }

            @Override
            public String preprocessingVersion() {
                return "finbert-v1";
            }

            @Override
            public SentimentResult analyze(NewsItem item) {
                return new SentimentResult(
                        item.newsId(), label, score, MODEL, item.inputVersion(),
                        "finbert-v1", NOW, summary);
            }
        };
    }
}
