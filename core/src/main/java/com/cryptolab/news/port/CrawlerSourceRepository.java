package com.cryptolab.news.port;

import com.cryptolab.news.domain.CrawlerSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrawlerSourceRepository {

    CrawlerSource create(CrawlerSource source);

    List<CrawlerSource> findAll();

    List<CrawlerSource> findEnabled();

    Optional<CrawlerSource> find(UUID sourceId);

    CrawlerSource update(CrawlerSource source, int expectedVersion);

    void recordSuccess(UUID sourceId, Instant at);

    void recordFailure(UUID sourceId, String error, Instant at);
}
