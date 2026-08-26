package com.cryptolab.infrastructure.news.adapter.html;

import com.cryptolab.news.domain.CrawlerSource;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.CrawlerSourceRepository;
import com.cryptolab.news.port.NewsProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public final class ConfigurableHtmlNewsProvider implements NewsProvider {

    private final CrawlerSourceRepository sources;
    private final HtmlTransport transport;
    private final Set<String> allowedHosts;
    private final Clock clock;
    private final int maximumItems;

    public ConfigurableHtmlNewsProvider(
            CrawlerSourceRepository sources,
            Set<String> allowedHosts,
            Duration connectTimeout,
            Duration requestTimeout,
            Clock clock,
            int maximumItems) {
        this(sources, new JdkHtmlTransport(connectTimeout, requestTimeout), allowedHosts, clock, maximumItems);
    }

    ConfigurableHtmlNewsProvider(
            CrawlerSourceRepository sources,
            HtmlTransport transport,
            Set<String> allowedHosts,
            Clock clock,
            int maximumItems) {
        this.sources = sources;
        this.transport = transport;
        this.allowedHosts = allowedHosts.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.clock = clock;
        if (maximumItems < 1 || maximumItems > 200) {
            throw new IllegalArgumentException("maximumItems must be between 1 and 200");
        }
        this.maximumItems = maximumItems;
    }

    @Override
    public List<NewsItem> fetchSince(Instant since) {
        List<CrawlerSource> enabled = sources.findEnabled();
        List<NewsItem> collected = new ArrayList<>();
        int failures = 0;
        for (CrawlerSource source : enabled) {
            try {
                collected.addAll(crawl(source, since));
                sources.recordSuccess(source.id(), clock.instant());
            } catch (RuntimeException failure) {
                failures++;
                sources.recordFailure(source.id(), safeMessage(failure), clock.instant());
            }
        }
        if (!enabled.isEmpty() && failures == enabled.size()) {
            throw new IllegalStateException("All enabled crawler sources failed");
        }
        return collected.stream()
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed()
                        .thenComparing(NewsItem::newsId))
                .limit(maximumItems)
                .toList();
    }

    private List<NewsItem> crawl(CrawlerSource source, Instant since) {
        URI listUri = allowed(source.listUrl());
        var list = Jsoup.parse(transport.get(listUri), listUri.toString());
        List<NewsItem> items = new ArrayList<>();
        for (Element summary : list.select(source.articleSelector())) {
            String href = requiredElement(summary, source.linkSelector(), "link").absUrl("href");
            URI articleUri = allowed(href);
            var article = Jsoup.parse(transport.get(articleUri), articleUri.toString());
            String title = text(summary, article, source.titleSelector(), "title");
            String content = text(article, summary, source.contentSelector(), "content");
            Element published = first(article, summary, source.publishedAtSelector(), "published time");
            String publishedValue = published.hasAttr("datetime")
                    ? published.attr("datetime") : published.text();
            Instant publishedAt = parseInstant(publishedValue);
            if (!publishedAt.isAfter(since)) continue;
            List<String> relatedCoins = source.relatedCoinsSelector().isBlank()
                    ? List.of()
                    : article.select(source.relatedCoinsSelector()).stream()
                            .flatMap(element -> java.util.Arrays.stream(element.text().split("[,|\\s]+")))
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .map(value -> value.toUpperCase(Locale.ROOT))
                            .distinct()
                            .toList();
            String normalized = normalize(title + " " + content);
            items.add(new NewsItem(
                    "html:" + sha256(articleUri.toString()), source.name(), title,
                    articleUri.toString(), publishedAt, normalized, "sha256:" + sha256(normalized),
                    content, clock.instant(), relatedCoins));
            if (items.size() >= maximumItems) break;
        }
        return items;
    }

    private URI allowed(String value) {
        URI uri = URI.create(value);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || !allowedHosts.contains(host)) {
            throw new IllegalArgumentException("Crawler host is not allowlisted: " + host);
        }
        return uri;
    }

    private static Element requiredElement(Element root, String selector, String label) {
        Element value = root.selectFirst(selector);
        if (value == null) throw new IllegalArgumentException("Crawler could not extract " + label);
        return value;
    }

    private static Element first(Element preferred, Element fallback, String selector, String label) {
        Element value = preferred.selectFirst(selector);
        return value == null ? requiredElement(fallback, selector, label) : value;
    }

    private static String text(Element preferred, Element fallback, String selector, String label) {
        String value = first(preferred, fallback, selector, label).text().trim();
        if (value.isBlank()) throw new IllegalArgumentException("Crawler extracted blank " + label);
        return value;
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ignored) {
            return OffsetDateTime.parse(value.trim()).toInstant();
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
