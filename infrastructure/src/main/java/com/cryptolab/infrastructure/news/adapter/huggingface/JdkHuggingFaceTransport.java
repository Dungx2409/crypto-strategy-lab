package com.cryptolab.infrastructure.news.adapter.huggingface;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class JdkHuggingFaceTransport implements HuggingFaceTransport {

    private final HttpClient client;
    private final Duration requestTimeout;

    JdkHuggingFaceTransport(Duration connectTimeout, Duration requestTimeout) {
        client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String classify(URI endpoint, String token, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        try {
            HttpResponse<String> response = client.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Hugging Face returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException failure) {
            throw new IllegalStateException("Hugging Face request failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hugging Face request interrupted", failure);
        }
    }
}
