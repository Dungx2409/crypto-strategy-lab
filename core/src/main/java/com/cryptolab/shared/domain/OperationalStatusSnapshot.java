package com.cryptolab.shared.domain;

public record OperationalStatusSnapshot(
        boolean brokerAvailable,
        long queueDepth,
        int workerConsumers,
        long runningJobs,
        long pendingOutboxEvents) {

    public OperationalStatusSnapshot {
        if (queueDepth < 0 || workerConsumers < 0 || runningJobs < 0 || pendingOutboxEvents < 0) {
            throw new IllegalArgumentException("operational status counts must not be negative");
        }
    }
}
