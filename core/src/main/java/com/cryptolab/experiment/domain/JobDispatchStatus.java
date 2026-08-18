package com.cryptolab.experiment.domain;

public enum JobDispatchStatus {
    PENDING_DISPATCH,
    QUEUED,
    RUNNING,
    RETRY_PENDING,
    COMPLETED,
    CANCELLED,
    FAILED
}
