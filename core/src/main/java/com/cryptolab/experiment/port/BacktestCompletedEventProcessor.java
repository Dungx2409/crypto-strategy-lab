package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.EventProcessingOutcome;
import com.cryptolab.shared.domain.DomainEventEnvelope;

@FunctionalInterface
public interface BacktestCompletedEventProcessor {

    EventProcessingOutcome process(DomainEventEnvelope<BacktestCompletedEvent> event);
}
