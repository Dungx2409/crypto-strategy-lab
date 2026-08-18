package com.cryptolab.shared.port;

import com.cryptolab.shared.domain.DomainEventEnvelope;

public interface DomainEventPublisher {
    void publish(DomainEventEnvelope<?> event);
}
