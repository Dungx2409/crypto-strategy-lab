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

    JdkCryptoCompareTransport(Duration connectTimeout, Duration requestTimeout) {
        client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "CryptoStrategyLab/0.1")
                .GET()
                .build();
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
}
