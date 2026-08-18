package com.cryptolab.experiment.domain;

public enum BacktestJobClaimDecision {
    CLAIMED,
    AWAITING_DISPATCH_CONFIRMATION,
    COMPLETED,
    IN_PROGRESS,
    CANCELLED,
    FAILED,
    NOT_FOUND
}
