package com.cryptolab.experiment.domain;

public enum ExperimentStatus {
    CREATED,
    QUEUED,
    RUNNING,
    RETRY_PENDING,
    COMPLETED,
    CANCELLED,
    FAILED
}
