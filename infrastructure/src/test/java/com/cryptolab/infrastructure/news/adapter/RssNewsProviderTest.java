package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssNewsProviderTest {

    @Test
    void normalizesRssArticlesAndAppliesTheCoinFilter() {
        URI feed = URI.create("https://feeds.example.com/crypto.xml");
        String xml = """
                <rss version="2.0"><channel>
                  <item><title>Bitcoin market update</title>
                    <link>https://news.example.com/btc</link>
                    <pubDate>Wed, 02 Sep 2026 08:00:00 GMT</pubDate>
                    <description><![CDATA[<p>BTC moved higher.</p>]]></description>
                  </item>
                  <item><title>Ethereum update</title>
                    <link>https://news.example.com/eth</link>
                    <pubDate>Wed, 02 Sep 2026 09:00:00 GMT</pubDate>
                    <description>ETH network news.</description>
                  </item>
                </channel></rss>
                """;
        var provider = new RssNewsProvider(List.of(feed), ignored -> xml, 20);

        var items = provider.fetchSince(Instant.parse("2026-09-01T00:00:00Z"), "BTC");

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().provider()).isEqualTo("RSS feeds.example.com");
        assertThat(items.getFirst().title()).isEqualTo("Bitcoin market update");
        assertThat(items.getFirst().normalizedText()).contains("BTC moved higher");
        assertThat(items.getFirst().inputVersion()).startsWith("sha256:");
    }

    @Test
    void readsAtomEntries() {
        URI feed = URI.create("https://atom.example.com/feed");
        String xml = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry><title>Solana update</title>
                    <link rel="alternate" href="https://news.example.com/sol"/>
                    <updated>2026-09-02T10:00:00Z</updated>
                    <summary>SOL activity increased.</summary>
                  </entry>
                </feed>
                """;
        var provider = new RssNewsProvider(List.of(feed), ignored -> xml, 20);

        var items = provider.fetchSince(Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(items).singleElement()
                .extracting(item -> item.url())
                .isEqualTo("https://news.example.com/sol");
    }
}
