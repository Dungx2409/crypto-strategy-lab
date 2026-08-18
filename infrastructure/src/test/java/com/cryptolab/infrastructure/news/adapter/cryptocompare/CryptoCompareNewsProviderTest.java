package com.cryptolab.infrastructure.news.adapter.cryptocompare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CryptoCompareNewsProviderTest {

    @Test
    void normalizesFiltersAndVersionsProviderPayload() {
        AtomicReference<URI> requested = new AtomicReference<>();
        CryptoCompareTransport transport = uri -> {
            requested.set(uri);
            return """
                    {"Type":1000,"Data":[
                      {"id":"42","source":"feed-a","title":"Bitcoin rally",
                       "url":"https://example.test/42","published_on":1787054400,
                       "body":"<p>Adoption &amp; growth</p>","source_info":{"name":"Example Feed"}},
                      {"id":"old","source":"feed-b","title":"Old article",
                       "url":"https://example.test/old","published_on":1787050000,"body":"old"}
                    ]}
                    """;
        };
        CryptoCompareNewsProvider provider = new CryptoCompareNewsProvider(
                transport,
                new ObjectMapper(),
                URI.create("https://news.test/data/v2/news/"),
                10);

        var items = provider.fetchSince(Instant.ofEpochSecond(1787051000));

        assertThat(requested.get().getQuery())
                .contains("lang=EN", "sortOrder=latest", "extraParams=CryptoStrategyLab");
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.newsId()).isEqualTo("cryptocompare:42");
            assertThat(item.provider()).isEqualTo("Example Feed");
            assertThat(item.normalizedText()).isEqualTo("Bitcoin rally Adoption & growth");
            assertThat(item.inputVersion()).startsWith("sha256:").hasSize(71);
        });
    }

    @Test
    void rejectsProviderErrorWithoutLeakingMalformedItems() {
        CryptoCompareNewsProvider provider = new CryptoCompareNewsProvider(
                uri -> "{\"Type\":\"100\",\"Message\":\"rate limited\"}",
                new ObjectMapper(),
                URI.create("https://news.test/api"),
                10);

        assertThatThrownBy(() -> provider.fetchSince(Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate limited");
    }
}
