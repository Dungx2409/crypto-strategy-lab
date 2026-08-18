package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.AsyncEvaluationService;
import com.cryptolab.experiment.application.AsyncRankingService;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.EventProcessingOutcome;
import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.experiment.port.AsyncEvaluationRepository;
import com.cryptolab.experiment.port.AsyncRankingRepository;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncEventPipelineTest {

    private static final UUID EXPERIMENT_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID SEARCH_RUN_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final UUID EVENT_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-18T15:00:00Z");
    private static final EvaluationMetrics METRICS =
            new EvaluationMetrics(new BigDecimal("12"), new BigDecimal("-4"), 8, new BigDecimal("8"));

    @Test
    void evaluationProducesAReproducibleCausallyLinkedEventAndAcknowledgesDuplicates() {
        RecordingEvaluationRepository repository = new RecordingEvaluationRepository();
        AsyncEvaluationService service =
                new AsyncEvaluationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.process(backtestCompleted())).isEqualTo(EventProcessingOutcome.PROCESSED);
        assertThat(service.process(backtestCompleted())).isEqualTo(EventProcessingOutcome.DUPLICATE);

        assertThat(repository.resultEvent.eventType()).isEqualTo("StrategyEvaluated");
        assertThat(repository.resultEvent.causationId()).isEqualTo(EVENT_ID.toString());
        assertThat(repository.resultEvent.correlationId()).isEqualTo("search-correlation");
        assertThat(repository.resultEvent.payload().metrics()).isEqualTo(METRICS);
        assertThat(repository.resultEvent.eventId().version()).isEqualTo(3);
    }

    @Test
    void rankingUsesAllCompletedEvaluationsAndEmitsOnlyTopFifty() {
        RecordingRankingRepository repository = new RecordingRankingRepository();
        for (int index = 0; index < 55; index++) {
            UUID id = UUID.nameUUIDFromBytes(("experiment-" + index).getBytes());
            repository.evaluations.add(new Evaluation(
                    id,
                    new EvaluationMetrics(
                            BigDecimal.valueOf(index),
                            BigDecimal.valueOf(-index),
                            index,
                            BigDecimal.valueOf(index)),
                    "evaluator-v1",
                    NOW));
        }
        AsyncRankingService service = new AsyncRankingService(
                repository, new DefaultRankingService(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.process(strategyEvaluated())).isEqualTo(EventProcessingOutcome.PROCESSED);
        assertThat(service.process(strategyEvaluated())).isEqualTo(EventProcessingOutcome.DUPLICATE);

        assertThat(repository.rankings).hasSize(50);
        assertThat(repository.rankings.getFirst().metrics().score()).isEqualByComparingTo("54");
        assertThat(repository.rankings.getLast().metrics().score()).isEqualByComparingTo("5");
        assertThat(repository.resultEvent.eventType()).isEqualTo("LeaderboardUpdated");
        assertThat(repository.resultEvent.orderingKey()).isEqualTo(SEARCH_RUN_ID.toString());
        assertThat(repository.resultEvent.payload().rankings()).isEqualTo(repository.rankings);
    }

    private static DomainEventEnvelope<BacktestCompletedEvent> backtestCompleted() {
        return new DomainEventEnvelope<>(
                EVENT_ID,
                "BacktestCompleted",
                1,
                NOW.minusSeconds(1),
                "Experiment",
                EXPERIMENT_ID,
                "search-correlation",
                null,
                EXPERIMENT_ID.toString(),
                new BacktestCompletedEvent(
                        EXPERIMENT_ID, SEARCH_RUN_ID, METRICS, "evaluator-v1", NOW.minusSeconds(1)));
    }

    private static DomainEventEnvelope<StrategyEvaluatedEvent> strategyEvaluated() {
        return new DomainEventEnvelope<>(
                EVENT_ID,
                "StrategyEvaluated",
                1,
                NOW,
                "Experiment",
                EXPERIMENT_ID,
                "search-correlation",
                UUID.randomUUID().toString(),
                EXPERIMENT_ID.toString(),
                new StrategyEvaluatedEvent(
                        EXPERIMENT_ID, SEARCH_RUN_ID, METRICS, "evaluator-v1", NOW));
    }

    private static final class RecordingEvaluationRepository implements AsyncEvaluationRepository {
        private boolean processed;
        private DomainEventEnvelope<StrategyEvaluatedEvent> resultEvent;

        @Override
        public boolean processOnce(
                DomainEventEnvelope<BacktestCompletedEvent> sourceEvent,
                Evaluation evaluation,
                DomainEventEnvelope<StrategyEvaluatedEvent> resultEvent,
                Instant processedAt) {
            this.resultEvent = resultEvent;
            if (processed) {
                return false;
            }
            processed = true;
            return true;
        }
    }

    private static final class RecordingRankingRepository implements AsyncRankingRepository {
        private final java.util.ArrayList<Evaluation> evaluations = new java.util.ArrayList<>();
        private boolean processed;
        private List<Ranking> rankings;
        private DomainEventEnvelope<LeaderboardUpdatedEvent> resultEvent;

        @Override
        public List<Evaluation> findCompletedEvaluations(UUID searchRunId) {
            return List.copyOf(evaluations);
        }

        @Override
        public boolean replaceLeaderboardOnce(
                DomainEventEnvelope<StrategyEvaluatedEvent> sourceEvent,
                List<Ranking> rankings,
                DomainEventEnvelope<LeaderboardUpdatedEvent> resultEvent,
                Instant processedAt) {
            this.rankings = rankings;
            this.resultEvent = resultEvent;
            if (processed) {
                return false;
            }
            processed = true;
            return true;
        }
    }
}
