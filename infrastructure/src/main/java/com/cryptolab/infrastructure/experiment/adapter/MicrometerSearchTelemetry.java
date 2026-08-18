package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.port.SearchTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class MicrometerSearchTelemetry implements SearchTelemetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerSearchTelemetry.class);

    private final AtomicInteger activeRuns = new AtomicInteger();
    private final Counter generatedCandidates;
    private final MeterRegistry registry;

    public MicrometerSearchTelemetry(MeterRegistry registry) {
        this.registry = registry;
        generatedCandidates = Counter.builder("crypto.candidates.generated")
                .description("Candidate strategies generated across search runs")
                .register(registry);
        Gauge.builder("crypto.search.runs.active", activeRuns, AtomicInteger::get)
                .description("Search runs currently executing candidate generation")
                .register(registry);
    }

    @Override
    public void runStarted(UUID searchRunId, String generatorType) {
        activeRuns.incrementAndGet();
        LOGGER.info("search_run_started correlationId={} searchRunId={} generatorType={}",
                searchRunId, searchRunId, generatorType);
    }

    @Override
    public void candidatesGenerated(UUID searchRunId, long count) {
        generatedCandidates.increment(count);
    }

    @Override
    public void runFinished(UUID searchRunId, SearchRunStatus status) {
        activeRuns.updateAndGet(current -> Math.max(0, current - 1));
        Counter.builder("crypto.search.runs.finished")
                .tag("status", status.name().toLowerCase())
                .register(registry)
                .increment();
        LOGGER.info("search_run_finished correlationId={} searchRunId={} status={}",
                searchRunId, searchRunId, status);
    }
}
