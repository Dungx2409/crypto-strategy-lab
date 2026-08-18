package com.cryptolab.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

class MicrometerWorkerTelemetryTest {

    @Test
    void exposesWorkerCountProcessedOutcomeQueueDepthAndFailureMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerWorkerTelemetry telemetry = new MicrometerWorkerTelemetry(
                registry, mock(ConnectionFactory.class), "worker-scale-1");

        telemetry.record(BacktestWorkerOutcome.COMPLETED);
        telemetry.record(BacktestWorkerOutcome.DEAD_LETTER);
        telemetry.recordPoisonMessage();
        telemetry.recordInfrastructureFailure();
        telemetry.recordDuration(Duration.ofMillis(25));

        assertThat(registry.find("crypto.backtest.worker.active")
                        .tag("worker", "worker-scale-1")
                        .gauge()
                        .value())
                .isEqualTo(1);
        assertThat(registry.find("crypto.backtest.worker.jobs")
                        .tag("worker", "worker-scale-1")
                        .tag("outcome", "completed")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.find("crypto.backtest.worker.failures")
                        .tag("worker", "worker-scale-1")
                        .counter()
                        .count())
                .isEqualTo(3);
        assertThat(registry.find("crypto.backtest.queue.depth").gauge()).isNotNull();
        assertThat(registry.find("crypto.backtest.jobs.started").counter().count()).isEqualTo(2);
        assertThat(registry.find("crypto.backtest.jobs.completed").counter().count()).isEqualTo(1);
        assertThat(registry.find("crypto.backtest.jobs.failed").counter().count()).isEqualTo(3);
        assertThat(registry.find("crypto.backtest.job.duration").timer().count()).isEqualTo(1);
    }
}
