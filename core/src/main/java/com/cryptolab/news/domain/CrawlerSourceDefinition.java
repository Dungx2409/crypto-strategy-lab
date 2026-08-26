package com.cryptolab.news.domain;

public record CrawlerSourceDefinition(
        String name,
        String listUrl,
        String articleSelector,
        String titleSelector,
        String linkSelector,
        String contentSelector,
        String publishedAtSelector,
        String relatedCoinsSelector,
        boolean enabled) {}
