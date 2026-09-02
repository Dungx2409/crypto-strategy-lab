package com.cryptolab.news.port;

import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.domain.NewsItem;
import java.time.Instant;
import java.util.List;

public interface CrawlerArticleExtractor {
    List<NewsItem> extract(
            String siteUrl, String html, CrawlerSelectors selectors, Instant crawledAt);
}
