package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.port.NewsProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeNewsProviderTest {

    @Test
    void keepsWorkingWhenOneProviderFailsAndDeduplicatesByUrl() {
        NewsItem older = item("one", "https://news.example.com/shared", "2026-09-02T08:00:00Z");
        NewsItem newer = item("two", "https://news.example.com/shared", "2026-09-02T09:00:00Z");
        NewsProvider first = provider("RSS", List.of(newer));
        NewsProvider second = provider("API", List.of(older));
        NewsProvider failed = new NewsProvider() {
            @Override public String name() { return "Failed"; }
            @Override public List<NewsItem> fetchSince(Instant since) {
                throw new IllegalStateException("offline");
            }
        };
        var composite = new CompositeNewsProvider(List.of(first, failed, second), 20);

        var items = composite.fetchSince(Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(items).containsExactly(newer);
        assertThat(composite.name()).isEqualTo("RSS + Failed + API");
    }

    private static NewsProvider provider(String name, List<NewsItem> items) {
        return new NewsProvider() {
            @Override public String name() { return name; }
            @Override public List<NewsItem> fetchSince(Instant since) { return items; }
        };
    }

    private static NewsItem item(String id, String url, String at) {
        return new NewsItem(id, "test", "Title", url, Instant.parse(at), "BTC update", "v1");
    }
}
