package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EventProcessingOutcome;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.experiment.port.AsyncEvaluationRepository;
import com.cryptolab.experiment.port.BacktestCompletedEventProcessor;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AsyncEvaluationService implements BacktestCompletedEventProcessor {

    public static final String SOURCE_EVENT_TYPE = "BacktestCompleted";
    public static final String RESULT_EVENT_TYPE = "StrategyEvaluated";
    private static final int SCHEMA_VERSION = 1;

    private final AsyncEvaluationRepository repository;
    private final Clock clock;

    public AsyncEvaluationService(AsyncEvaluationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public EventProcessingOutcome process(DomainEventEnvelope<BacktestCompletedEvent> event) {
        requireEnvelope(event);
        BacktestCompletedEvent payload = event.payload();
        Instant evaluatedAt = clock.instant();
        Evaluation evaluation = new Evaluation(
                payload.experimentId(), payload.metrics(), payload.evaluatorVersion(), evaluatedAt);
        StrategyEvaluatedEvent result = new StrategyEvaluatedEvent(
                payload.experimentId(),
                payload.searchRunId(),
                payload.metrics(),
                payload.evaluatorVersion(),
                evaluatedAt);
        DomainEventEnvelope<StrategyEvaluatedEvent> resultEnvelope = new DomainEventEnvelope<>(
                derivedEventId(RESULT_EVENT_TYPE, event.eventId()),
                RESULT_EVENT_TYPE,
                SCHEMA_VERSION,
                evaluatedAt,
                "Experiment",
                payload.experimentId(),
                event.correlationId(),
                event.eventId().toString(),
                payload.experimentId().toString(),
                result);
        return repository.processOnce(event, evaluation, resultEnvelope, evaluatedAt)
                ? EventProcessingOutcome.PROCESSED
                : EventProcessingOutcome.DUPLICATE;
    }

    private static void requireEnvelope(DomainEventEnvelope<BacktestCompletedEvent> event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!SOURCE_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("expected " + SOURCE_EVENT_TYPE + " event");
        }
        if (!event.aggregateId().equals(event.payload().experimentId())) {
            throw new IllegalArgumentException("event aggregate and experiment identity mismatch");
        }
    }

    private static UUID derivedEventId(String eventType, UUID sourceEventId) {
        return UUID.nameUUIDFromBytes(
                (eventType + ":" + sourceEventId).getBytes(StandardCharsets.UTF_8));
    }
}
