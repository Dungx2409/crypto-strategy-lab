package com.cryptolab.infrastructure.news.adapter.cryptocompare;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.NewsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public final class CryptoCompareNewsProvider implements NewsProvider {

    private final CryptoCompareTransport transport;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final int maximumItems;

    public CryptoCompareNewsProvider(
            ObjectMapper objectMapper,
            URI endpoint,
            Duration connectTimeout,
            Duration requestTimeout,
            int maximumItems) {
        this(new JdkCryptoCompareTransport(connectTimeout, requestTimeout), objectMapper, endpoint, maximumItems);
    }

    CryptoCompareNewsProvider(
            CryptoCompareTransport transport,
            ObjectMapper objectMapper,
            URI endpoint,
            int maximumItems) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        if (maximumItems < 1 || maximumItems > 200) {
            throw new IllegalArgumentException("maximumItems must be between 1 and 200");
        }
        this.maximumItems = maximumItems;
    }

    @Override
    public List<NewsItem> fetchSince(Instant since) {
        Objects.requireNonNull(since, "since must not be null");
        JsonNode root = parse(transport.get(requestUri()));
        if ("100".equals(root.path("Type").asText()) || !root.path("Data").isArray()) {
            throw new IllegalStateException(
                    "CryptoCompare response error: " + root.path("Message").asText("missing Data array"));
        }
        return StreamSupport.stream(root.path("Data").spliterator(), false)
                .map(CryptoCompareNewsProvider::map)
                .filter(item -> item.publishedAt().isAfter(since))
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed()
                        .thenComparing(NewsItem::newsId))
                .limit(maximumItems)
                .toList();
    }

    private URI requestUri() {
        String separator = endpoint.toString().contains("?") ? "&" : "?";
        return URI.create(endpoint + separator + "lang=EN&sortOrder=latest&extraParams=CryptoStrategyLab");
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception malformedPayload) {
            throw new IllegalStateException("CryptoCompare returned malformed JSON", malformedPayload);
        }
    }

    private static NewsItem map(JsonNode article) {
        String id = required(article, "id");
        String title = required(article, "title");
        String url = required(article, "url");
        long publishedEpoch = article.path("published_on").asLong(0);
        if (publishedEpoch <= 0) {
            throw new IllegalArgumentException("CryptoCompare article published_on is missing");
        }
        String source = article.path("source_info").path("name").asText();
        if (source.isBlank()) {
            source = article.path("source").asText("CryptoCompare");
        }
        source = bounded(source, 64);
        String normalized = normalize(title + " " + article.path("body").asText(""));
        return new NewsItem(
                bounded("cryptocompare:" + id, 256),
                source,
                title,
                url,
                Instant.ofEpochSecond(publishedEpoch),
                normalized,
                "sha256:" + sha256(normalized));
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("CryptoCompare article " + field + " is missing");
        }
        return value.trim();
    }

    private static String normalize(String text) {
        String value = text.replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("CryptoCompare article text is empty");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String bounded(String value, int maximumLength) {
        String trimmed = value.trim();
        return trimmed.length() <= maximumLength ? trimmed : trimmed.substring(0, maximumLength);
    }
}
