package com.cryptolab.api.news;

import com.cryptolab.news.application.CrawlerTemplateService;
import com.cryptolab.news.application.CrawlerNewsCollectionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class CrawlerTemplateMonitor {
    private final CrawlerTemplateService templates;
    private final CrawlerNewsCollectionService collections;

    CrawlerTemplateMonitor(
            CrawlerTemplateService templates, CrawlerNewsCollectionService collections) {
        this.templates = templates;
        this.collections = collections;
    }

    @Scheduled(fixedDelayString = "${crypto.news.crawler-check-interval:15m}")
    void check() {
        templates.checkAll();
        collections.collectAll();
    }
}
