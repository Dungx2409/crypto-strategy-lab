package com.cryptolab.api.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.infrastructure.news.adapter.SelectableNewsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NewsProviderConfigurationTest {

    private final NewsRuntimeConfiguration configuration = new NewsRuntimeConfiguration();

    @Test
    void selectsRssAndCompositeModes() {
        ObjectMapper mapper = new ObjectMapper();
        String feeds = "https://feeds.example.com/rss,https://atom.example.com/feed";
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences("rss");

        var selectable = configuration.newsProvider(
                mapper, preferences, URI.create("https://api.example.com/news"), "",
                feeds, Duration.ofSeconds(1), Duration.ofSeconds(2), 20);

        assertThat(selectable).isInstanceOf(SelectableNewsProvider.class);
        assertThat(selectable.name()).isEqualTo("RSS / Atom");
        preferences.update("5m", "ALL", "ALL");
        assertThat(selectable.name()).isEqualTo("CryptoCompare + RSS / Atom");
    }

    @Test
    void rssModeRequiresAtLeastOneFeed() {
        assertThatThrownBy(() -> configuration.newsProvider(
                new ObjectMapper(), new MutableNewsFeedPreferences(),
                URI.create("https://api.example.com/news"), "",
                "", Duration.ofSeconds(1), Duration.ofSeconds(2), 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEWS_RSS_URLS");
    }
}
