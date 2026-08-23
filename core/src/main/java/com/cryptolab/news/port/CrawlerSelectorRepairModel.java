package com.cryptolab.news.port;

import com.cryptolab.news.domain.CrawlerSelectors;

public interface CrawlerSelectorRepairModel {
    CrawlerSelectors repair(String siteUrl, CrawlerSelectors previous, String sampleHtml, String failure);
}
