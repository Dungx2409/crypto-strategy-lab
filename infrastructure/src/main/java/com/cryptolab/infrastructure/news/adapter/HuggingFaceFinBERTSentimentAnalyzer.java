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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

public final class HuggingFaceFinBERTSentimentAnalyzer implements SentimentAnalyzer {

    private static final String PREPROCESSING_VERSION = "finbert-v1";
    private static final ModelDescriptor DESCRIPTOR = new ModelDescriptor("finbert", "v1");

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
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.modelUrl = modelUrl;
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
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("HUGGINGFACE_API_KEY is blank; set it before using FinBERT sentiment analysis");
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("inputs", item.normalizedText());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("HuggingFace API failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode responseTree = objectMapper.readTree(response.body());
            if (!responseTree.isArray() || responseTree.isEmpty()) {
                throw new IllegalStateException("Unexpected HuggingFace API response format: " + response.body());
            }

            JsonNode predictions = responseTree.get(0);
            if (!predictions.isArray() || predictions.isEmpty()) {
                throw new IllegalStateException("Unexpected HuggingFace API response array format: " + response.body());
            }

            String bestLabel = "neutral";
            double maxScore = -1.0;

            for (JsonNode prediction : predictions) {
                String label = prediction.get("label").asText().toLowerCase();
                double score = prediction.get("score").asDouble();
                if (score > maxScore) {
                    maxScore = score;
                    bestLabel = label;
                }
            }

            SentimentLabel sentiment = mapLabel(bestLabel);
            return new SentimentResult(
                    item.newsId(),
                    sentiment,
                    BigDecimal.valueOf(maxScore),
                    DESCRIPTOR,
                    item.inputVersion(),
                    PREPROCESSING_VERSION,
                    clock.instant()
            );

        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to call HuggingFace API: " + ex.getMessage(), ex);
        }
    }

    private SentimentLabel mapLabel(String label) {
        if (label.contains("positive")) {
            return SentimentLabel.POSITIVE;
        }
        if (label.contains("negative")) {
            return SentimentLabel.NEGATIVE;
        }
        return SentimentLabel.NEUTRAL;
    }
}
