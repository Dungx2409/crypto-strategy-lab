package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.ManualRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
final class ManualRunRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualRunRecovery.class);

    private final ManualRunService service;
    private final TaskExecutor executor;

    ManualRunRecovery(
            ManualRunService service,
            @Qualifier("searchTaskExecutor") TaskExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recover() {
        service.recoverableBatchIds().forEach(batchId -> executor.execute(() -> {
            try {
                service.execute(batchId);
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "manual_run_recovery_failed batchId={} errorType={}",
                        batchId,
                        failure.getClass().getSimpleName(),
                        failure);
            }
        }));
    }
}
