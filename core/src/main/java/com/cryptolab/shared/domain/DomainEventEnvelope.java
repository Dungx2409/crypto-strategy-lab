package com.cryptolab.shared.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DomainEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String causationId,
        String orderingKey,
        T payload) {

    public DomainEventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        eventType = requireText(eventType, "eventType");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        aggregateType = requireText(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        correlationId = requireText(correlationId, "correlationId");
        orderingKey = requireText(orderingKey, "orderingKey");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
