package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HuggingFaceFinBERTSentimentAnalyzerTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final NewsItem ITEM = new NewsItem(
            "news-1", "source", "Bitcoin rally", "https://example.com/1",
            NOW.minusSeconds(30), "Bitcoin rally continues after ETF approval", "input-v1");

    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/finbert", exchange -> {
            byte[] body = """
                    [[{"label":"positive","score":0.87},{"label":"neutral","score":0.1},{"label":"negative","score":0.03}]]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/finbert";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsFinBertConfidenceToSignedScore() {
        var analyzer = new HuggingFaceFinBERTSentimentAnalyzer(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                endpoint,
                "hf-test-key");

        var result = analyzer.analyze(ITEM);

        assertThat(result.sentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.8700"));
        assertThat(result.model().name()).isEqualTo("finbert");
        assertThat(result.preprocessingVersion()).isEqualTo("finbert-v1");
        assertThat(result.summary()).isNull();
    }

    @Test
    void rejectsBlankApiKey() {
        var analyzer = new HuggingFaceFinBERTSentimentAnalyzer(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                endpoint,
                " ");

        assertThatThrownBy(() -> analyzer.analyze(ITEM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HUGGINGFACE_API_KEY");
    }
}
