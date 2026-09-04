package com.cryptolab.api.news;

import com.cryptolab.news.application.NewsCollector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
final class NewsCollectionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewsCollectionScheduler.class);

    private final NewsCollector collector;
    private final MutableNewsFeedPreferences preferences;
    private final TaskScheduler taskScheduler;
    private final ExecutorService executor;
    private final AtomicBoolean collectionRunning = new AtomicBoolean();
    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> scheduled;

    NewsCollectionScheduler(
            NewsCollector collector,
            MutableNewsFeedPreferences preferences,
            @Qualifier("newsCollectionTaskScheduler") TaskScheduler taskScheduler,
            @Qualifier("newsCollectionExecutor") ExecutorService executor) {
        this.collector = collector;
        this.preferences = preferences;
        this.taskScheduler = taskScheduler;
        this.executor = executor;
    }

    @PostConstruct
    void start() {
        reschedule(preferences.intervalDuration());
    }

    @PreDestroy
    void stop() {
        synchronized (scheduleLock) {
            if (scheduled != null) {
                scheduled.cancel(false);
                scheduled = null;
            }
        }
    }

    void reschedule(Duration interval) {
        synchronized (scheduleLock) {
            if (scheduled != null) {
                scheduled.cancel(false);
                scheduled = null;
            }
            if (interval == null || interval.isZero() || interval.isNegative()) {
                LOGGER.info("news_collection_disabled");
                return;
            }
            scheduled = taskScheduler.scheduleWithFixedDelay(
                    this::collect,
                    Instant.now().plusSeconds(30),
                    interval);
            LOGGER.info("news_collection_rescheduled interval={}", interval);
        }
    }

    private void collect() {
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
