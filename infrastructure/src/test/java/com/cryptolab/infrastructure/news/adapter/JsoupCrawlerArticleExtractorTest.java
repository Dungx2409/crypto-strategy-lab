package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.domain.CrawlerSelectors;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JsoupCrawlerArticleExtractorTest {

    @Test
    void extractsNormalizedArticlesWithAbsoluteLinksAndDates() {
        var extractor = new JsoupCrawlerArticleExtractor();
        var items = extractor.extract(
                "https://news.example.com/crypto",
                """
                <article><h2>Bitcoin rises</h2><a href="/btc">Read</a>
                <time datetime="2026-09-01T08:00:00Z"></time><p>Market update</p></article>
                """,
                new CrawlerSelectors("article", "h2", "a", "time"),
                Instant.parse("2026-09-01T09:00:00Z"));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().provider()).isEqualTo("html-news.example.com");
        assertThat(items.getFirst().title()).isEqualTo("Bitcoin rises");
        assertThat(items.getFirst().url()).isEqualTo("https://news.example.com/btc");
        assertThat(items.getFirst().publishedAt()).isEqualTo("2026-09-01T08:00:00Z");
        assertThat(items.getFirst().normalizedText()).contains("Bitcoin rises", "Market update");
    }
}
