package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.NewsFeedPreferences;
import com.cryptolab.news.port.NewsProvider;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SelectableNewsProvider implements NewsProvider {

    private final NewsFeedPreferences preferences;
    private final Map<String, NewsProvider> providers;

    public SelectableNewsProvider(
            NewsFeedPreferences preferences,
            NewsProvider cryptoCompare,
            NewsProvider rss,
            int maximumItems) {
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
        NewsProvider all = new CompositeNewsProvider(
                List.of(cryptoCompare, rss), maximumItems);
        this.providers = Map.of(
                "CRYPTOCOMPARE", cryptoCompare,
                "RSS", rss,
                "ALL", all);
    }

    @Override
    public String name() {
        return selected().name();
    }

    @Override
    public List<NewsItem> fetchSince(Instant since) {
        return selected().fetchSince(since);
    }

    @Override
    public List<NewsItem> fetchSince(Instant since, String categoriesCsv) {
        return selected().fetchSince(since, categoriesCsv);
    }

    private NewsProvider selected() {
        String code = preferences.providerCode();
        String normalized = code == null ? "ALL" : code.trim().toUpperCase(Locale.ROOT);
        NewsProvider provider = providers.get(normalized);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "News provider must be CryptoCompare, RSS, or All providers");
        }
        return provider;
    }
}
