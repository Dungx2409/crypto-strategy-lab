package com.cryptolab.experiment.domain;

public enum BacktestWorkerOutcome {
    COMPLETED,
    DUPLICATE_ACKNOWLEDGED,
    RETRY_SCHEDULED,
    REQUEUE,
    DEAD_LETTER
}
