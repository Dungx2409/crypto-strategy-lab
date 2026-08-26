package com.cryptolab.infrastructure.news.adapter.html;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.domain.CrawlerSource;
import com.cryptolab.news.port.CrawlerSourceRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfigurableHtmlNewsProviderTest {

    @Test
    void extractsConfiguredFieldsAndRecordsSourceHealth() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        CrawlerSource source = new CrawlerSource(
                UUID.randomUUID(), "Example", "https://news.example/list", ".item", ".title",
                "a", ".content", "time", ".coin", true, 1, 0, null, now, now);
        FakeSources sources = new FakeSources(source);
        HtmlTransport transport = uri -> uri.getPath().equals("/list")
                ? "<article class='item'><a href='/story'><span class='title'>Bitcoin rises</span></a></article>"
                : "<main><time datetime='2026-08-23T12:00:00Z'></time><p class='content'>ETF demand grows.</p><span class='coin'>BTC</span></main>";
        var provider = new ConfigurableHtmlNewsProvider(
                sources, transport, Set.of("news.example"),
                Clock.fixed(now, ZoneOffset.UTC), 10);

        var items = provider.fetchSince(Instant.parse("2026-08-23T00:00:00Z"));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Bitcoin rises");
            assertThat(item.content()).isEqualTo("ETF demand grows.");
            assertThat(item.relatedCoins()).containsExactly("BTC");
            assertThat(item.crawledAt()).isEqualTo(now);
        });
        assertThat(sources.successes).isEqualTo(1);
    }

    private static final class FakeSources implements CrawlerSourceRepository {
        private final CrawlerSource source;
        private int successes;

        private FakeSources(CrawlerSource source) {
            this.source = source;
        }

        @Override public CrawlerSource create(CrawlerSource value) { return value; }
        @Override public List<CrawlerSource> findAll() { return List.of(source); }
        @Override public List<CrawlerSource> findEnabled() { return List.of(source); }
        @Override public Optional<CrawlerSource> find(UUID sourceId) { return Optional.of(source); }
        @Override public CrawlerSource update(CrawlerSource value, int version) { return value; }
        @Override public void recordSuccess(UUID sourceId, Instant at) { successes++; }
        @Override public void recordFailure(UUID sourceId, String error, Instant at) {}
    }
}
