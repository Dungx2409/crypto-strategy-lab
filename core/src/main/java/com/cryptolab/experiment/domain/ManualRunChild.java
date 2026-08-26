package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Timeframe;
import java.util.UUID;

public record ManualRunChild(
        UUID id,
        Timeframe timeframe,
        ManualRunStatus status,
        UUID experimentId,
        String failureMessage) {}
