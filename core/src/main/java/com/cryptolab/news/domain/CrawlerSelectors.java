package com.cryptolab.news.domain;

public record CrawlerSelectors(
        String itemSelector,
        String titleSelector,
        String linkSelector,
        String dateSelector) {

    public CrawlerSelectors {
        itemSelector = required(itemSelector, "itemSelector");
        titleSelector = required(titleSelector, "titleSelector");
        linkSelector = required(linkSelector, "linkSelector");
        dateSelector = dateSelector == null ? "" : dateSelector.trim();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw new IllegalArgumentException(name + " must contain 1 to 500 characters");
        }
        return value.trim();
    }
}
