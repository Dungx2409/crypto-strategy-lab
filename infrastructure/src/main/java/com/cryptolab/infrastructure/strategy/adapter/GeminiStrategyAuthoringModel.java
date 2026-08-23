package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.port.StrategyAuthoringModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class GeminiStrategyAuthoringModel implements StrategyAuthoringModel {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public GeminiStrategyAuthoringModel(
            ObjectMapper objectMapper,
            @Value("${crypto.ai.gemini.api-key:}") String apiKey,
            @Value("${crypto.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${crypto.ai.gemini.timeout:30s}") Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), objectMapper, apiKey, model, timeout);
    }

    GeminiStrategyAuthoringModel(
            HttpClient client, ObjectMapper objectMapper, String apiKey, String model, Duration timeout) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public String proposeIdea(String prompt, List<StrategyPluginDescriptor> availableStrategies) {
        return generate("""
                You design cryptocurrency backtest strategies. Explain one concrete strategy idea in plain language.
                Use only the registered plugins listed below. Do not output JSON yet.

                User request:
                %s

                Registered plugins:
                %s
                """.formatted(prompt, json(availableStrategies)));
    }

    @Override
    public String generateJson(
            String prompt,
            String confirmedIdea,
            List<StrategyPluginDescriptor> availableStrategies,
            String previousOutput,
            String validationError) {
        String repair = previousOutput == null ? "" : """

                The previous JSON was rejected.
                Validation error: %s
                Previous output: %s
                Fix the error and return the complete JSON object again.
                """.formatted(validationError, previousOutput);
        return generate("""
                Convert the confirmed idea into one restricted JSON object. Output JSON only, without Markdown.
                The exact shape is:
                {"name":"...","description":"...","strategies":[{"type":"...","version":"...","parameters":{}}],"combinationPolicy":{"type":"MAJORITY","version":"1.0","weights":{},"threshold":0}}
                Use only registered type, version, and parameter names. Never output source code.

                User request: %s
                Confirmed idea: %s
                Registered plugins: %s
                %s
                """.formatted(prompt, confirmedIdea, json(availableStrategies), repair));
    }

    private String generate(String prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is blank; set it before using strategy authoring");
        }
        try {
            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("temperature", 0.2)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent?key=" + apiKey))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gemini request failed with HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode text = root.at("/candidates/0/content/parts/0/text");
            if (!text.isTextual() || text.asText().isBlank()) {
                throw new IllegalStateException("Gemini returned no text");
            }
            return text.asText().trim();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Gemini request failed", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize Gemini prompt", exception);
        }
    }
}
