package com.cryptolab.infrastructure.experiment.messaging;

import java.time.Instant;
import java.util.UUID;

public record BacktestJobOutboxMessage(
        UUID eventId,
        UUID experimentId,
        String eventType,
        int schemaVersion,
        String payloadJson,
        String destination,
        String routingKey,
        int attemptCount,
        Instant createdAt) {}
