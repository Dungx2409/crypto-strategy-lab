package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.port.CrawlerSelectorMatcher;
import org.jsoup.Jsoup;
import org.jsoup.select.Selector.SelectorParseException;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public final class JsoupCrawlerSelectorMatcher implements CrawlerSelectorMatcher {

    @Override
    public boolean matches(String html, CrawlerSelectors selectors) {
        try {
            var items = Jsoup.parse(html).select(selectors.itemSelector());
            if (items.isEmpty()) return false;
            for (Element item : items) {
                if (item.selectFirst(selectors.titleSelector()) != null
                        && item.selectFirst(selectors.linkSelector()) != null
                        && (selectors.dateSelector().isEmpty()
                                || item.selectFirst(selectors.dateSelector()) != null)) {
                    return true;
                }
            }
            return false;
        } catch (SelectorParseException invalidSelector) {
            return false;
        }
    }
}
