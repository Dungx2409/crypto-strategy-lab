package com.cryptolab.worker;

import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import com.cryptolab.infrastructure.experiment.messaging.BacktestJobTopology;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class MicrometerWorkerTelemetry implements WorkerTelemetry {

    private final Map<BacktestWorkerOutcome, Counter> outcomes;
    private final Counter failures;
    private final Counter started;
    private final Counter completed;
    private final Counter failed;
    private final Counter duplicates;
    private final Timer duration;
    private final RabbitAdmin rabbitAdmin;

    public MicrometerWorkerTelemetry(
            MeterRegistry registry,
            ConnectionFactory connectionFactory,
            @Value("${crypto.backtest.worker.id:${HOSTNAME:crypto-worker}}") String workerId) {
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
        EnumMap<BacktestWorkerOutcome, Counter> counters =
                new EnumMap<>(BacktestWorkerOutcome.class);
        for (BacktestWorkerOutcome outcome : BacktestWorkerOutcome.values()) {
            counters.put(
                    outcome,
                    Counter.builder("crypto.backtest.worker.jobs")
                            .description("Backtest deliveries processed by outcome")
                            .tag("worker", workerId)
                            .tag("outcome", outcome.name().toLowerCase())
                            .register(registry));
        }
        outcomes = Map.copyOf(counters);
        failures = Counter.builder("crypto.backtest.worker.failures")
                .description("Poison or infrastructure failures observed by a worker")
                .tag("worker", workerId)
                .register(registry);
        started = Counter.builder("crypto.backtest.jobs.started")
                .description("Valid backtest deliveries submitted to the worker service")
                .tag("worker", workerId)
                .register(registry);
        completed = Counter.builder("crypto.backtest.jobs.completed")
                .description("Backtest experiments completed by this worker")
                .tag("worker", workerId)
                .register(registry);
        failed = Counter.builder("crypto.backtest.jobs.failed")
                .description("Poison, exhausted, or infrastructure-failed backtest deliveries")
                .tag("worker", workerId)
                .register(registry);
        duplicates = Counter.builder("crypto.backtest.duplicate.delivery")
                .description("Duplicate deliveries acknowledged without duplicate artifacts")
                .tag("worker", workerId)
                .register(registry);
        duration = Timer.builder("crypto.backtest.job.duration")
                .description("Duration of a worker processing attempt")
                .tag("worker", workerId)
                .register(registry);
        Gauge.builder("crypto.backtest.worker.active", () -> 1)
                .description("One per active worker process; sum across replicas for worker count")
                .tag("worker", workerId)
                .register(registry);
        Gauge.builder("crypto.backtest.queue.depth", this, MicrometerWorkerTelemetry::queueDepth)
                .description("Ready message count in the durable backtest queue")
                .register(registry);
    }

    @Override
    public void record(BacktestWorkerOutcome outcome) {
        started.increment();
        outcomes.get(outcome).increment();
        if (outcome == BacktestWorkerOutcome.COMPLETED) {
            completed.increment();
        }
        if (outcome == BacktestWorkerOutcome.DUPLICATE_ACKNOWLEDGED) {
            duplicates.increment();
        }
        if (outcome == BacktestWorkerOutcome.DEAD_LETTER) {
            failures.increment();
            failed.increment();
        }
    }

    @Override
    public void recordDuration(Duration elapsed) {
        duration.record(elapsed);
    }

    @Override
    public void recordPoisonMessage() {
        failures.increment();
        failed.increment();
    }

    @Override
    public void recordInfrastructureFailure() {
        failures.increment();
        failed.increment();
    }

    private double queueDepth() {
        try {
            QueueInformation information = rabbitAdmin.getQueueInfo(BacktestJobTopology.JOB_QUEUE);
            return information == null ? 0 : information.getMessageCount();
        } catch (RuntimeException unavailableBroker) {
            return Double.NaN;
        }
    }
}
