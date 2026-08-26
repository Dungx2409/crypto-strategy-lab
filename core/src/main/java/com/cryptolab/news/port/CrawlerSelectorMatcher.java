package com.cryptolab.news.port;

import com.cryptolab.news.domain.CrawlerSelectors;

public interface CrawlerSelectorMatcher {
    boolean matches(String html, CrawlerSelectors selectors);
}
