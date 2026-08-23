package com.cryptolab.news.domain;

import java.time.Instant;
import java.util.UUID;

public record CrawlerTemplateVersion(
        UUID templateId,
        UUID accountId,
        String siteUrl,
        int version,
        CrawlerSelectors selectors,
        String status,
        String repairReason,
        Instant createdAt) {}
