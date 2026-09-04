package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.port.ArticleSourceReader;
import com.cryptolab.news.port.CrawlerPageReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public final class HttpArticleSourceReader implements ArticleSourceReader, CrawlerPageReader {

    private static final int MAX_BYTES = 200_000;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public String read(String url) {
        return plainText(readPage(url));
    }

    @Override
    public String readPage(String url) {
        URI uri = validate(url);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "text/html,text/plain")
                    .header("User-Agent", "CryptoStrategyLab/1.0")
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalArgumentException("Article returned HTTP " + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (!contentType.contains("text/html") && !contentType.contains("text/plain")) {
                throw new IllegalArgumentException("Article URL must return HTML or plain text");
            }
            byte[] bytes;
            try (var body = response.body()) {
                bytes = body.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Article is larger than 200 KB");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Article download was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Article could not be downloaded", exception);
        }
    }

    private static URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("articleUrl must not be blank");
        }
        URI uri = URI.create(url.trim());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("articleUrl must be a public HTTP or HTTPS URL");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("articleUrl must not resolve to a private address");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("articleUrl host could not be resolved", exception);
        }
        return uri;
    }

    static String plainText(String html) {
        String text = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Article contains no readable text");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("requires javascript verification")
                || lower.contains("enable javascript")
                || lower.contains("checking your browser")
                || lower.contains("verify you are human")
                || lower.contains("just a moment")) {
            throw new IllegalArgumentException(
                    "This article page requires JavaScript verification. Paste the article text into the fallback field instead.");
        }
        return text.length() > 20_000 ? text.substring(0, 20_000) : text;
    }
}
