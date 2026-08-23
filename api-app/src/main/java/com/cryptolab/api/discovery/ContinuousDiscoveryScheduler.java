package com.cryptolab.api.discovery;

import com.cryptolab.experiment.application.ContinuousDiscoveryService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class ContinuousDiscoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContinuousDiscoveryScheduler.class);
    private final ContinuousDiscoveryService service;
    private final TaskExecutor executor;
    private final AtomicBoolean ticking = new AtomicBoolean();

    ContinuousDiscoveryScheduler(ContinuousDiscoveryService service, TaskExecutor searchTaskExecutor) {
        this.service = service;
        this.executor = searchTaskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recover() {
        service.recoverInterruptedRuns();
        tick();
    }

    @Scheduled(fixedDelayString = "${crypto.discovery.poll-interval:1m}")
    void tick() {
        if (!ticking.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                service.tick();
            } catch (RuntimeException failure) {
                LOGGER.error("continuous_discovery_tick_failed errorType={}",
                        failure.getClass().getSimpleName(), failure);
            } finally {
                ticking.set(false);
            }
        });
    }
}
