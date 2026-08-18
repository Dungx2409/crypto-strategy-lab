package com.cryptolab.infrastructure.news.adapter.cryptocompare;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JdkCryptoCompareTransportTest {

    @Test
    void sendsApiKeyUsingAuthorizationHeader() {
        JdkCryptoCompareTransport transport = new JdkCryptoCompareTransport(
                Duration.ofSeconds(1), Duration.ofSeconds(2), " secret-key ");

        var request = transport.request(URI.create("https://news.test/data/v2/news/"));

        assertThat(request.headers().firstValue("Authorization"))
                .contains("Apikey secret-key");
        assertThat(request.uri().getQuery()).isNull();
    }

    @Test
    void omitsAuthorizationHeaderWhenApiKeyIsBlank() {
        JdkCryptoCompareTransport transport = new JdkCryptoCompareTransport(
                Duration.ofSeconds(1), Duration.ofSeconds(2), "  ");

        var request = transport.request(URI.create("https://news.test/data/v2/news/"));

        assertThat(request.headers().firstValue("Authorization")).isEmpty();
    }
}
