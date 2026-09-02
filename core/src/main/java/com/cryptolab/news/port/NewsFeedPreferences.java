package com.cryptolab.news.port;

/**
 * Runtime news feed preferences for providers that support category filtering.
 * Blank categories means "all coins".
 */
public interface NewsFeedPreferences {

    String categoriesCsv();

    default String providerCode() {
        return "ALL";
    }

    static NewsFeedPreferences allCoins() {
        return () -> "";
    }
}
