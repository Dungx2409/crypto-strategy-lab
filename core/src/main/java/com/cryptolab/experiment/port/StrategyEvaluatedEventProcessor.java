package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.EventProcessingOutcome;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.shared.domain.DomainEventEnvelope;

@FunctionalInterface
public interface StrategyEvaluatedEventProcessor {

    EventProcessingOutcome process(DomainEventEnvelope<StrategyEvaluatedEvent> event);
}
