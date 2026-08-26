package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.domain.CrawlerSelectors;
import org.junit.jupiter.api.Test;

class JsoupCrawlerSelectorMatcherTest {

    private final JsoupCrawlerSelectorMatcher matcher = new JsoupCrawlerSelectorMatcher();

    @Test
    void requiresEachConfiguredSelectorInsideAnItem() {
        String html = "<article class='story'><h2>Title</h2><a href='/story'>Read</a><time>now</time></article>";

        assertThat(matcher.matches(html,
                new CrawlerSelectors("article.story", "h2", "a[href]", "time"))).isTrue();
        assertThat(matcher.matches(html,
                new CrawlerSelectors("article.story", "h1", "a[href]", "time"))).isFalse();
    }

    @Test
    void rejectsInvalidCssWithoutBreakingTheMonitor() {
        assertThat(matcher.matches("<article></article>",
                new CrawlerSelectors("article[", "h2", "a", ""))).isFalse();
    }
}
