package com.cryptolab.infrastructure.news.adapter.html;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class JdkHtmlTransport implements HtmlTransport {

    private final HttpClient client;
    private final Duration requestTimeout;

    JdkHtmlTransport(Duration connectTimeout, Duration requestTimeout) {
        client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "CryptoStrategyLab/0.1")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Crawler returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException failure) {
            throw new IllegalStateException("Crawler request failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crawler request interrupted", failure);
        }
    }
}
