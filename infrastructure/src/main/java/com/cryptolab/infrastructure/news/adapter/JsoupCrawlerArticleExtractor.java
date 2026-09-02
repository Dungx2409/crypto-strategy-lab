package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.CrawlerArticleExtractor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public final class JsoupCrawlerArticleExtractor implements CrawlerArticleExtractor {

    private static final int MAX_ITEMS = 50;

    @Override
    public List<NewsItem> extract(
            String siteUrl, String html, CrawlerSelectors selectors, Instant crawledAt) {
        var document = Jsoup.parse(html, siteUrl);
        String provider = "html-" + URI.create(siteUrl).getHost();
        List<NewsItem> items = new ArrayList<>();
        for (Element item : document.select(selectors.itemSelector())) {
            if (items.size() == MAX_ITEMS) break;
            Element title = item.selectFirst(selectors.titleSelector());
            Element link = item.selectFirst(selectors.linkSelector());
            if (title == null || link == null) continue;
            String url = link.absUrl("href");
            if (url.isBlank()) continue;
            String text = item.text().trim();
            if (title.text().isBlank() || text.isBlank()) continue;
            Instant publishedAt = publishedAt(item, selectors.dateSelector(), crawledAt);
            items.add(new NewsItem(
                    sha256(url), provider, title.text(), url, publishedAt,
                    text, sha256(text)));
        }
        return List.copyOf(items);
    }

    private static Instant publishedAt(Element item, String selector, Instant fallback) {
        if (selector.isBlank()) return fallback;
        Element date = item.selectFirst(selector);
        if (date == null) return fallback;
        String value = date.hasAttr("datetime") ? date.attr("datetime") : date.text();
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (RuntimeException invalidDate) {
                return fallback;
            }
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
}
