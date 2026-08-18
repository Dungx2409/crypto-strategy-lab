package com.cryptolab.api.news;

import com.cryptolab.news.application.NewsCollector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class NewsCollectionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewsCollectionScheduler.class);

    private final NewsCollector collector;
    private final ExecutorService executor;
    private final AtomicBoolean collectionRunning = new AtomicBoolean();

    NewsCollectionScheduler(
            NewsCollector collector,
            @Qualifier("newsCollectionExecutor") ExecutorService executor) {
        this.collector = collector;
        this.executor = executor;
    }

    @Scheduled(
            initialDelayString = "${crypto.news.collection.initial-delay:30s}",
            fixedDelayString = "${crypto.news.collection.interval:5m}")
    void collect() {
        if (collectionRunning.compareAndSet(false, true)) {
            executor.execute(() -> {
                try {
                    collector.collect();
                } catch (RuntimeException failure) {
                    LOGGER.error("news_collection_async_failed errorType={}",
                            failure.getClass().getSimpleName(), failure);
                } finally {
                    collectionRunning.set(false);
                }
            });
        }
    }
}
