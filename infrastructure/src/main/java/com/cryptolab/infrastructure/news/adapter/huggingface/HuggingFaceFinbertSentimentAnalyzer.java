package com.cryptolab.infrastructure.news.adapter.huggingface;

import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.SentimentAnalyzer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public final class HuggingFaceFinbertSentimentAnalyzer implements SentimentAnalyzer {

    public static final String PREPROCESSING_VERSION = "normalized-text-4000-v1";

    private final HuggingFaceTransport transport;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String token;
    private final ModelDescriptor model;
    private final Clock clock;

    public HuggingFaceFinbertSentimentAnalyzer(
            ObjectMapper objectMapper,
            URI endpoint,
            String token,
            String modelName,
            String modelRevision,
            Duration connectTimeout,
            Duration requestTimeout,
            Clock clock) {
        this(new JdkHuggingFaceTransport(connectTimeout, requestTimeout), objectMapper,
                endpoint, token, modelName, modelRevision, clock);
    }

    HuggingFaceFinbertSentimentAnalyzer(
            HuggingFaceTransport transport,
            ObjectMapper objectMapper,
            URI endpoint,
            String token,
            String modelName,
            String modelRevision,
            Clock clock) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.token = token == null ? "" : token;
        this.model = new ModelDescriptor(modelName, modelRevision);
        this.clock = clock;
    }

    @Override
    public ModelDescriptor descriptor() {
        return model;
    }

    @Override
    public String preprocessingVersion() {
        return PREPROCESSING_VERSION;
    }

    @Override
    public SentimentResult analyze(NewsItem item) {
        String input = item.normalizedText().substring(0, Math.min(4000, item.normalizedText().length()));
        String request = json(Map.of(
                "inputs", input,
                "parameters", Map.of("function_to_apply", "softmax", "top_k", 3)));
        JsonNode root = parse(transport.classify(endpoint, token, request));
        JsonNode scores = root.isArray() && root.size() == 1 && root.get(0).isArray()
                ? root.get(0) : root;
        if (!scores.isArray()) {
            throw new IllegalStateException("Hugging Face returned an invalid classification response");
        }
        BigDecimal positive = BigDecimal.ZERO;
        BigDecimal negative = BigDecimal.ZERO;
        BigDecimal neutral = BigDecimal.ZERO;
        for (JsonNode result : scores) {
            String label = result.path("label").asText().toLowerCase(Locale.ROOT);
            BigDecimal score = result.path("score").decimalValue();
            if (label.contains("positive")) positive = score;
            else if (label.contains("negative")) negative = score;
            else if (label.contains("neutral")) neutral = score;
        }
        if (positive.signum() == 0 && negative.signum() == 0 && neutral.signum() == 0) {
            throw new IllegalStateException("Hugging Face response did not contain FinBERT labels");
        }
        SentimentLabel label = positive.compareTo(negative) >= 0 && positive.compareTo(neutral) >= 0
                ? SentimentLabel.POSITIVE
                : negative.compareTo(neutral) >= 0 ? SentimentLabel.NEGATIVE : SentimentLabel.NEUTRAL;
        return new SentimentResult(
                item.newsId(), label, positive.subtract(negative), model,
                item.inputVersion(), PREPROCESSING_VERSION, clock.instant());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("sentiment request could not be serialized", exception);
        }
    }

    private JsonNode parse(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Hugging Face returned malformed JSON", exception);
        }
    }
}
