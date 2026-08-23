package com.cryptolab.news.application;

import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.domain.CrawlerTemplateVersion;
import com.cryptolab.news.port.CrawlerSelectorRepairModel;
import com.cryptolab.news.port.CrawlerTemplateRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class CrawlerTemplateService {
    private final CrawlerTemplateRepository repository;
    private final CrawlerSelectorRepairModel repairModel;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public CrawlerTemplateService(
            CrawlerTemplateRepository repository,
            CrawlerSelectorRepairModel repairModel,
            Clock clock,
            Supplier<UUID> ids) {
        this.repository = repository;
        this.repairModel = repairModel;
        this.clock = clock;
        this.ids = ids;
    }

    public CrawlerTemplateVersion create(UUID accountId, String siteUrl, CrawlerSelectors selectors) {
        if (siteUrl == null || siteUrl.isBlank()) throw new IllegalArgumentException("siteUrl must not be blank");
        return repository.create(ids.get(), accountId, siteUrl.trim(), selectors, clock.instant());
    }

    public List<CrawlerTemplateVersion> list(UUID accountId) {
        return repository.findCurrentByAccount(accountId);
    }

    public List<CrawlerTemplateVersion> versions(UUID accountId, UUID templateId) {
        return repository.findVersions(accountId, templateId);
    }

    public CrawlerTemplateVersion repair(
            UUID accountId, UUID templateId, String sampleHtml, String failure) {
        if (sampleHtml == null || sampleHtml.isBlank() || sampleHtml.length() > 50_000) {
            throw new IllegalArgumentException("sampleHtml must contain 1 to 50000 characters");
        }
        CrawlerTemplateVersion current = repository.findCurrent(accountId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Crawler template was not found: " + templateId));
        CrawlerSelectors repaired = repairModel.repair(
                current.siteUrl(), current.selectors(), sampleHtml, failure);
        return repository.addRepair(accountId, templateId, repaired, failure, clock.instant());
    }

    public CrawlerTemplateVersion confirm(UUID accountId, UUID templateId, int version) {
        return repository.activate(accountId, templateId, version, clock.instant());
    }
}
