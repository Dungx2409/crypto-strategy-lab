package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.SentimentAnalyzer;
import java.util.Objects;
import java.util.function.Function;

/**
 * Keeps the delegate's label/score identity and asks Gemini for a short sentiment paragraph.
 */
public final class GeminiSentimentSummaryDecorator implements SentimentAnalyzer {

    private final SentimentAnalyzer delegate;
    private final Function<String, String> generate;

    public GeminiSentimentSummaryDecorator(SentimentAnalyzer delegate, Function<String, String> generate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.generate = Objects.requireNonNull(generate, "generate must not be null");
    }

    @Override
    public ModelDescriptor descriptor() {
        return delegate.descriptor();
    }

    @Override
    public String preprocessingVersion() {
        return delegate.preprocessingVersion();
    }

    @Override
    public SentimentResult analyze(NewsItem item) {
        SentimentResult scored = delegate.analyze(item);
        if (scored.summary() != null) {
            return scored;
        }
        try {
            String summary = generate.apply("""
                    Write one short paragraph (2-4 sentences) summarizing the market sentiment of this \
                    cryptocurrency news article. Use the given label and score as ground truth. \
                    Plain text only, no markdown, no JSON.
                    Label: %s
                    Score: %s
                    Source: %s
                    Title and body: %s
                    """.formatted(
                    scored.sentiment().name(),
                    scored.score().toPlainString(),
                    item.provider(),
                    item.normalizedText()));
            if (summary == null || summary.isBlank()) {
                return scored;
            }
            return scored.withSummary(summary.trim());
        } catch (RuntimeException ignored) {
            return scored;
        }
    }
}
