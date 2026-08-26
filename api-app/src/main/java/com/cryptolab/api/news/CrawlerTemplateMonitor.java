package com.cryptolab.api.news;

import com.cryptolab.news.application.CrawlerTemplateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class CrawlerTemplateMonitor {
    private final CrawlerTemplateService templates;

    CrawlerTemplateMonitor(CrawlerTemplateService templates) {
        this.templates = templates;
    }

    @Scheduled(fixedDelayString = "${crypto.news.crawler-check-interval:15m}")
    void check() {
        templates.checkAll();
    }
}
