package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.NewsProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class CompositeNewsProvider implements NewsProvider {

    private final List<NewsProvider> providers;
    private final int maximumItems;

    public CompositeNewsProvider(List<NewsProvider> providers, int maximumItems) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers must not be null"));
        if (this.providers.isEmpty()) throw new IllegalArgumentException("providers must not be empty");
        if (maximumItems < 1 || maximumItems > 500) {
            throw new IllegalArgumentException("maximumItems must be between 1 and 500");
        }
        this.maximumItems = maximumItems;
    }

    @Override
    public String name() {
        return providers.stream().map(NewsProvider::name).distinct()
                .reduce((left, right) -> left + " + " + right).orElse("Composite");
    }

    @Override
    public List<NewsItem> fetchSince(Instant since) {
        return fetchSince(since, "");
    }

    @Override
    public List<NewsItem> fetchSince(Instant since, String categoriesCsv) {
        LinkedHashMap<String, NewsItem> byUrl = new LinkedHashMap<>();
        List<RuntimeException> failures = new ArrayList<>();
        for (NewsProvider provider : providers) {
            try {
                provider.fetchSince(since, categoriesCsv)
                        .forEach(item -> byUrl.merge(item.url(), item,
                                (left, right) -> right.publishedAt().isAfter(left.publishedAt())
                                        ? right : left));
            } catch (RuntimeException failure) {
                failures.add(failure);
            }
        }
        if (byUrl.isEmpty() && failures.size() == providers.size()) {
            throw new IllegalStateException("All configured news providers failed", failures.getFirst());
        }
        return byUrl.values().stream()
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed()
                        .thenComparing(NewsItem::newsId))
                .limit(maximumItems)
                .toList();
    }
}
