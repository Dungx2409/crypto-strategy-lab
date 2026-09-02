package com.cryptolab.news.application;

import com.cryptolab.news.domain.CrawlerTemplateVersion;
import com.cryptolab.news.domain.NewsCollectionResult;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.CrawlerArticleExtractor;
import com.cryptolab.news.port.CrawlerPageReader;
import com.cryptolab.news.port.CrawlerTemplateRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class CrawlerNewsCollectionService {
    private static final System.Logger LOGGER =
            System.getLogger(CrawlerNewsCollectionService.class.getName());
    private static final int MAX_ARTICLE_CHARS = 20_000;

    private final CrawlerTemplateRepository templates;
    private final CrawlerPageReader pages;
    private final CrawlerArticleExtractor extractor;
    private final NewsCollector collector;
    private final Clock clock;

    public CrawlerNewsCollectionService(
            CrawlerTemplateRepository templates,
            CrawlerPageReader pages,
            CrawlerArticleExtractor extractor,
            NewsCollector collector,
            Clock clock) {
        this.templates = templates;
        this.pages = pages;
        this.extractor = extractor;
        this.collector = collector;
        this.clock = clock;
    }

    public NewsCollectionResult collect(UUID accountId, UUID templateId) {
        CrawlerTemplateVersion template = templates.findCurrent(accountId, templateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Crawler template was not found: " + templateId));
        String html = pages.readPage(template.siteUrl());
        List<NewsItem> listingItems = extractor.extract(
                template.siteUrl(), html, template.selectors(), clock.instant());
        List<NewsItem> articles = new ArrayList<>(listingItems.size());
        for (NewsItem item : listingItems) {
            articles.add(enrichWithArticleText(item));
        }
        return collector.collect(List.copyOf(articles));
    }

    public void collectAll() {
        for (CrawlerTemplateVersion template : templates.findAllCurrent()) {
            try {
                collect(template.accountId(), template.templateId());
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Crawler collection failed for template " + template.templateId(), failure);
            }
        }
    }

    private NewsItem enrichWithArticleText(NewsItem item) {
        try {
            String articleText = plainText(pages.readPage(item.url()));
            if (articleText.length() <= item.normalizedText().length()) {
                return item;
            }
            return new NewsItem(
                    item.newsId(),
                    item.provider(),
                    item.title(),
                    item.url(),
                    item.publishedAt(),
                    articleText,
                    sha256(articleText));
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Using listing text for sentiment; article fetch failed for " + item.url(),
                    failure);
            return item;
        }
    }

    private static String plainText(String html) {
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
        return text.length() > MAX_ARTICLE_CHARS ? text.substring(0, MAX_ARTICLE_CHARS) : text;
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
