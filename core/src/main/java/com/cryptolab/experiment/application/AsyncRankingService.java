package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.EventProcessingOutcome;
import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.experiment.port.AsyncRankingRepository;
import com.cryptolab.experiment.port.StrategyEvaluatedEventProcessor;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AsyncRankingService implements StrategyEvaluatedEventProcessor {

    public static final String SOURCE_EVENT_TYPE = "StrategyEvaluated";
    public static final String RESULT_EVENT_TYPE = "LeaderboardUpdated";
    public static final int LEADERBOARD_LIMIT = 50;
    private static final int SCHEMA_VERSION = 1;

    private final AsyncRankingRepository repository;
    private final DefaultRankingService rankingService;
    private final Clock clock;

    public AsyncRankingService(
            AsyncRankingRepository repository,
            DefaultRankingService rankingService,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.rankingService = Objects.requireNonNull(rankingService, "rankingService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public EventProcessingOutcome process(DomainEventEnvelope<StrategyEvaluatedEvent> event) {
        requireEnvelope(event);
        StrategyEvaluatedEvent payload = event.payload();
        List<Ranking> rankings = rankingService
                .rank(repository.findCompletedEvaluations(payload.searchRunId()))
                .stream()
                .limit(LEADERBOARD_LIMIT)
                .toList();
        Instant updatedAt = clock.instant();
        LeaderboardUpdatedEvent result =
                new LeaderboardUpdatedEvent(payload.searchRunId(), rankings, updatedAt);
        DomainEventEnvelope<LeaderboardUpdatedEvent> resultEnvelope = new DomainEventEnvelope<>(
                derivedEventId(RESULT_EVENT_TYPE, event.eventId()),
                RESULT_EVENT_TYPE,
                SCHEMA_VERSION,
                updatedAt,
                "SearchRun",
                payload.searchRunId(),
                event.correlationId(),
                event.eventId().toString(),
                payload.searchRunId().toString(),
                result);
        return repository.replaceLeaderboardOnce(event, rankings, resultEnvelope, updatedAt)
                ? EventProcessingOutcome.PROCESSED
                : EventProcessingOutcome.DUPLICATE;
    }

    private static void requireEnvelope(DomainEventEnvelope<StrategyEvaluatedEvent> event) {
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
