package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.NewsProvider;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class RssNewsProvider implements NewsProvider {

    private final List<URI> feeds;
    private final Function<URI, String> transport;
    private final int maximumItems;

    public RssNewsProvider(
            List<URI> feeds,
            Duration connectTimeout,
            Duration requestTimeout,
            int maximumItems) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.feeds = validatedFeeds(feeds);
        this.transport = uri -> fetch(client, uri, requestTimeout);
        this.maximumItems = validateMaximum(maximumItems);
    }

    RssNewsProvider(List<URI> feeds, Function<URI, String> transport, int maximumItems) {
        this.feeds = validatedFeeds(feeds);
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.maximumItems = validateMaximum(maximumItems);
    }

    @Override
    public String name() {
        return "RSS / Atom";
    }

    @Override
    public List<NewsItem> fetchSince(Instant since) {
        return fetchSince(since, "");
    }

    @Override
    public List<NewsItem> fetchSince(Instant since, String categoriesCsv) {
        Objects.requireNonNull(since, "since must not be null");
        Set<String> categories = categories(categoriesCsv);
        List<NewsItem> result = new ArrayList<>();
        RuntimeException lastFailure = null;
        for (URI feed : feeds) {
            try {
                parse(feed, transport.apply(feed)).stream()
                        .filter(item -> item.publishedAt().isAfter(since))
                        .filter(item -> matches(item, categories))
                        .forEach(result::add);
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        if (result.isEmpty() && lastFailure != null) throw lastFailure;
        return result.stream()
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed()
                        .thenComparing(NewsItem::newsId))
                .limit(maximumItems)
                .toList();
    }

    private static List<NewsItem> parse(URI feed, String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = elements(document, "item");
            boolean atom = nodes.getLength() == 0;
            if (atom) nodes = elements(document, "entry");
            List<NewsItem> items = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                Element entry = (Element) nodes.item(index);
                map(feed, entry, atom).ifPresent(items::add);
            }
            return List.copyOf(items);
        } catch (Exception invalidFeed) {
            throw new IllegalStateException("RSS feed returned invalid XML: " + feed, invalidFeed);
        }
    }

    private static java.util.Optional<NewsItem> map(URI feed, Element entry, boolean atom) {
        String title = text(entry, "title");
        String url = atom ? atomLink(entry) : text(entry, "link");
        String date = atom ? firstText(entry, "published", "updated")
                : firstText(entry, "pubDate", "published", "date");
        Instant publishedAt = instant(date);
        if (title.isBlank() || url.isBlank() || publishedAt == null) {
            return java.util.Optional.empty();
        }
        String body = firstText(entry, "description", "summary", "content", "encoded");
        String normalized = normalize(title + " " + body);
        String provider = bounded("RSS " + Objects.requireNonNullElse(feed.getHost(), "feed"), 64);
        return java.util.Optional.of(new NewsItem(
                "rss:" + sha256(url), provider, title.trim(), url.trim(), publishedAt,
                normalized, "sha256:" + sha256(normalized)));
    }

    private static NodeList elements(Document document, String name) {
        NodeList namespaced = document.getElementsByTagNameNS("*", name);
        return namespaced.getLength() > 0 ? namespaced : document.getElementsByTagName(name);
    }

    private static String firstText(Element parent, String... names) {
        return Arrays.stream(names).map(name -> text(parent, name))
                .filter(value -> !value.isBlank()).findFirst().orElse("");
    }

    private static String text(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagNameNS("*", "link");
        if (links.getLength() == 0) links = entry.getElementsByTagName("link");
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            String relation = link.getAttribute("rel");
            if (relation.isBlank() || "alternate".equals(relation)) {
                String href = link.getAttribute("href");
                if (!href.isBlank()) return href;
            }
        }
        return "";
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        for (Function<String, Instant> parser : List.<Function<String, Instant>>of(
                Instant::parse,
                text -> OffsetDateTime.parse(text).toInstant(),
                text -> ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())) {
            try {
                return parser.apply(value.trim());
            } catch (RuntimeException ignored) {
                // Try the next common feed date format.
            }
        }
        return null;
    }

    private static boolean matches(NewsItem item, Set<String> categories) {
        if (categories.isEmpty()) return true;
        String text = item.normalizedText().toUpperCase(Locale.ROOT);
        return categories.stream().anyMatch(text::contains);
    }

    private static Set<String> categories(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> values = new LinkedHashSet<>();
        for (String value : csv.split(",")) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isBlank()) values.add(normalized);
        }
        return Set.copyOf(values);
    }

    private static String normalize(String value) {
        String text = Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
        if (text.isBlank()) throw new IllegalArgumentException("RSS article text is empty");
        return text;
    }

    private static List<URI> validatedFeeds(List<URI> feeds) {
        List<URI> values = List.copyOf(Objects.requireNonNull(feeds, "feeds must not be null"));
        if (values.isEmpty()) throw new IllegalArgumentException("at least one RSS URL is required");
        for (URI uri : values) {
            if (uri == null || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("RSS URLs must use HTTP or HTTPS");
            }
        }
        return values;
    }

    private static int validateMaximum(int maximumItems) {
        if (maximumItems < 1 || maximumItems > 200) {
            throw new IllegalArgumentException("maximumItems must be between 1 and 200");
        }
        return maximumItems;
    }

    private static String fetch(HttpClient client, URI uri, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "RSS request failed with HTTP " + response.statusCode() + ": " + uri);
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RSS request was interrupted: " + uri, interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("RSS request failed: " + uri, failure);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String bounded(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
