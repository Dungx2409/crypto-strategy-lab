package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.infrastructure.strategy.adapter.GeminiStrategyAuthoringModel;
import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.SentimentAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

public final class GeminiSentimentAnalyzer implements SentimentAnalyzer {

    public static final String PREPROCESSING_VERSION = "normalized-news-text-v1";
    private final Function<String, String> generate;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ModelDescriptor descriptor;

    public GeminiSentimentAnalyzer(
            GeminiStrategyAuthoringModel gemini,
            ObjectMapper mapper,
            Clock clock,
            String modelVersion) {
        this(gemini::generateText, mapper, clock, modelVersion);
    }

    GeminiSentimentAnalyzer(
            Function<String, String> generate,
            ObjectMapper mapper,
            Clock clock,
            String modelVersion) {
        this.generate = Objects.requireNonNull(generate);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
        this.descriptor = new ModelDescriptor("gemini", modelVersion);
    }

    @Override public ModelDescriptor descriptor() { return descriptor; }
    @Override public String preprocessingVersion() { return PREPROCESSING_VERSION; }

    @Override
    public SentimentResult analyze(NewsItem item) {
        String output = generate.apply("""
                Analyze cryptocurrency-news sentiment. Return JSON only with this exact shape:
                {"label":"POSITIVE|NEUTRAL|NEGATIVE","score":0.0}
                Score must be between -1 and 1. Use the article meaning, not keyword counting.
                POSITIVE requires a positive score, NEGATIVE a negative score, and NEUTRAL exactly zero.
                Source: %s
                Title and body: %s
                """.formatted(item.provider(), item.normalizedText()));
        try {
            JsonNode root = mapper.readTree(output);
            JsonNode labelNode = root.get("label");
            JsonNode scoreNode = root.get("score");
            if (labelNode == null || !labelNode.isTextual() || scoreNode == null || !scoreNode.isNumber()) {
                throw new IllegalArgumentException("Gemini sentiment JSON requires label and numeric score");
            }
            SentimentLabel label = SentimentLabel.valueOf(labelNode.asText().trim().toUpperCase());
            BigDecimal score = scoreNode.decimalValue();
            if (score.compareTo(BigDecimal.ONE.negate()) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Gemini sentiment score must be between -1 and 1");
            }
            if ((label == SentimentLabel.POSITIVE && score.signum() <= 0)
                    || (label == SentimentLabel.NEGATIVE && score.signum() >= 0)
                    || (label == SentimentLabel.NEUTRAL && score.abs().compareTo(new BigDecimal("0.25")) > 0)) {
                throw new IllegalArgumentException("Gemini sentiment label and score are inconsistent");
            }
            return new SentimentResult(
                    item.newsId(), label, score, descriptor, item.inputVersion(),
                    PREPROCESSING_VERSION, clock.instant());
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Gemini returned invalid sentiment JSON", failure);
        }
    }
}
