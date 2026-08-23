package com.cryptolab.api.news;

import com.cryptolab.news.domain.CrawlerSelectors;

public record CrawlerTemplateRequest(
        String siteUrl,
        String itemSelector,
        String titleSelector,
        String linkSelector,
        String dateSelector) {
    CrawlerSelectors selectors() {
        return new CrawlerSelectors(itemSelector, titleSelector, linkSelector, dateSelector);
    }
}
