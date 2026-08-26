package com.cryptolab.news.application;

import com.cryptolab.news.domain.CrawlerSource;
import com.cryptolab.news.domain.CrawlerSourceDefinition;
import com.cryptolab.news.port.CrawlerSourceRepository;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class CrawlerSourceService {

    private final CrawlerSourceRepository sources;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final Set<String> allowedHosts;

    public CrawlerSourceService(
            CrawlerSourceRepository sources,
            Clock clock,
            Supplier<UUID> ids,
            Set<String> allowedHosts) {
        this.sources = sources;
        this.clock = clock;
        this.ids = ids;
        this.allowedHosts = allowedHosts.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public CrawlerSource create(CrawlerSourceDefinition definition) {
        validateHttpUrl(definition.listUrl());
        var now = clock.instant();
        return sources.create(new CrawlerSource(
                ids.get(), definition.name(), definition.listUrl(), definition.articleSelector(),
                definition.titleSelector(), definition.linkSelector(), definition.contentSelector(),
                definition.publishedAtSelector(), definition.relatedCoinsSelector(), definition.enabled(),
                1, 0, null, now, now));
    }

    public CrawlerSource update(
            UUID id, CrawlerSourceDefinition definition, int expectedVersion) {
        validateHttpUrl(definition.listUrl());
        CrawlerSource existing = get(id);
        return sources.update(new CrawlerSource(
                id, definition.name(), definition.listUrl(), definition.articleSelector(),
                definition.titleSelector(), definition.linkSelector(), definition.contentSelector(),
                definition.publishedAtSelector(), definition.relatedCoinsSelector(), definition.enabled(),
                existing.version() + 1,
                existing.consecutiveFailures(), existing.lastError(), existing.createdAt(),
                clock.instant()), expectedVersion);
    }

    public List<CrawlerSource> list() {
        return sources.findAll();
    }

    public CrawlerSource get(UUID id) {
        return sources.find(id).orElseThrow(() ->
                new IllegalArgumentException("Crawler source was not found: " + id));
    }

    private void validateHttpUrl(String value) {
        URI uri = URI.create(value);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("listUrl must be an absolute HTTP or HTTPS URL");
        }
        if (!allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Crawler host is not allowlisted: " + uri.getHost());
        }
    }
}
