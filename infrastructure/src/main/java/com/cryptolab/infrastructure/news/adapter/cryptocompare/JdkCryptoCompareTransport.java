package com.cryptolab.infrastructure.news.adapter.cryptocompare;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class JdkCryptoCompareTransport implements CryptoCompareTransport {

    private final HttpClient client;
    private final Duration requestTimeout;
    private final String apiKey;

    JdkCryptoCompareTransport(Duration connectTimeout, Duration requestTimeout, String apiKey) {
        client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    @Override
    public String get(URI uri) {
        HttpRequest request = request(uri);
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "CryptoCompare returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException failure) {
            throw new IllegalStateException("CryptoCompare request failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CryptoCompare request interrupted", failure);
        }
    }

    HttpRequest request(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "CryptoStrategyLab/0.1");
        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Apikey " + apiKey);
        }
        return builder.GET().build();
    }
}
