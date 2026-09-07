package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.SentimentAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class HuggingFaceFinBERTSentimentAnalyzer implements SentimentAnalyzer {

    private static final String PREPROCESSING_VERSION = "finbert-v1";
    private static final ModelDescriptor DESCRIPTOR = new ModelDescriptor("finbert", "v1");
    private static final int MAX_INPUT_WORDS = 180;
    private static final int MAX_INPUT_CHARS = 1_000;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String modelUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public HuggingFaceFinBERTSentimentAnalyzer(
            ObjectMapper objectMapper,
            Clock clock,
            String modelUrl,
            String apiKey) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.modelUrl = Objects.requireNonNull(modelUrl, "modelUrl must not be null");
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ModelDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public String preprocessingVersion() {
        return PREPROCESSING_VERSION;
    }

    @Override
    public SentimentResult analyze(NewsItem item) {
        Objects.requireNonNull(item, "item must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "HUGGINGFACE_API_KEY is blank; set it before using FinBERT sentiment analysis");
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("inputs", truncateForFinbert(item.normalizedText()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "HuggingFace API failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode responseTree = objectMapper.readTree(response.body());
            if (!responseTree.isArray() || responseTree.isEmpty()) {
                throw new IllegalStateException("Unexpected HuggingFace API response format: " + response.body());
            }

            JsonNode predictions = responseTree.get(0);
            if (!predictions.isArray() || predictions.isEmpty()) {
                throw new IllegalStateException(
                        "Unexpected HuggingFace API response array format: " + response.body());
            }

            String bestLabel = "neutral";
            double maxScore = -1.0;
            for (JsonNode prediction : predictions) {
                String label = prediction.path("label").asText("").toLowerCase();
                double score = prediction.path("score").asDouble(Double.NaN);
                if (Double.isNaN(score)) {
                    continue;
                }
                if (score > maxScore) {
                    maxScore = score;
                    bestLabel = label;
                }
            }
            if (maxScore < 0) {
                throw new IllegalStateException("HuggingFace API returned no usable FinBERT scores");
            }

            SentimentLabel sentiment = mapLabel(bestLabel);
            BigDecimal confidence = BigDecimal.valueOf(maxScore).setScale(4, RoundingMode.HALF_UP);
            BigDecimal signedScore = switch (sentiment) {
                case POSITIVE -> confidence;
                case NEGATIVE -> confidence.negate();
                case NEUTRAL -> BigDecimal.ZERO;
            };

            return new SentimentResult(
                    item.newsId(),
                    sentiment,
                    signedScore,
                    DESCRIPTOR,
                    item.inputVersion(),
                    PREPROCESSING_VERSION,
                    clock.instant());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String detail = ex.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = ex.getClass().getSimpleName();
            }
            throw new IllegalStateException("Failed to call HuggingFace API: " + detail, ex);
        }
    }

    private static SentimentLabel mapLabel(String label) {
        if (label.contains("positive")) {
            return SentimentLabel.POSITIVE;
        }
        if (label.contains("negative")) {
            return SentimentLabel.NEGATIVE;
        }
        return SentimentLabel.NEUTRAL;
    }

    private static String truncateForFinbert(String text) {
        String normalized = Objects.requireNonNull(text, "text must not be null").trim();
        if (normalized.length() <= MAX_INPUT_CHARS
                && normalized.split("\\s+").length <= MAX_INPUT_WORDS) {
            return normalized;
        }
        StringBuilder result = new StringBuilder(Math.min(normalized.length(), MAX_INPUT_CHARS));
        int wordCount = 0;
        for (String word : normalized.split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            if (result.length() > 0 && result.length() + 1 + word.length() > MAX_INPUT_CHARS) {
                break;
            }
            if (result.length() == 0 && word.length() > MAX_INPUT_CHARS) {
                return word.substring(0, MAX_INPUT_CHARS);
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word);
            wordCount++;
            if (wordCount >= MAX_INPUT_WORDS) {
                break;
            }
        }
        return result.toString();
    }
}
