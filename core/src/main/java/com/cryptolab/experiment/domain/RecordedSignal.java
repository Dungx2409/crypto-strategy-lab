package com.cryptolab.experiment.domain;

import com.cryptolab.strategy.domain.Signal;
import java.util.Objects;

public record RecordedSignal(String strategyType, String strategyVersion, Signal signal) {

    public RecordedSignal {
        if (strategyType == null || strategyType.isBlank()) {
            throw new IllegalArgumentException("strategyType must not be blank");
        }
        if (strategyVersion == null || strategyVersion.isBlank()) {
            throw new IllegalArgumentException("strategyVersion must not be blank");
        }
        Objects.requireNonNull(signal, "signal must not be null");
    }
}
