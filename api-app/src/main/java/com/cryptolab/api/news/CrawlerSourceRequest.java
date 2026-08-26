package com.cryptolab.api.news;

import com.cryptolab.news.domain.CrawlerSourceDefinition;

public record CrawlerSourceRequest(
        String name,
        String listUrl,
        String articleSelector,
        String titleSelector,
        String linkSelector,
        String contentSelector,
        String publishedAtSelector,
        String relatedCoinsSelector,
        boolean enabled) {

    CrawlerSourceDefinition toDefinition() {
        return new CrawlerSourceDefinition(
                name, listUrl, articleSelector, titleSelector, linkSelector, contentSelector,
                publishedAtSelector, relatedCoinsSelector, enabled);
    }
}
