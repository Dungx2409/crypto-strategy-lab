package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import java.time.Instant;

public interface AsyncEvaluationRepository {

    boolean processOnce(
            DomainEventEnvelope<BacktestCompletedEvent> sourceEvent,
            Evaluation evaluation,
            DomainEventEnvelope<StrategyEvaluatedEvent> resultEvent,
            Instant processedAt);
}
