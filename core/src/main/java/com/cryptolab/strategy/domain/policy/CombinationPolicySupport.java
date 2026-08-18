package com.cryptolab.strategy.domain.policy;

import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.WeightedSignal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

final class CombinationPolicySupport {

    private CombinationPolicySupport() {}

    static List<WeightedSignal> requireSignals(List<WeightedSignal> signals) {
        Objects.requireNonNull(signals, "signals must not be null");
        if (signals.isEmpty()) {
            throw new IllegalArgumentException("at least one signal is required");
        }
        return List.copyOf(signals);
    }

    static int direction(SignalType type) {
        return switch (type) {
            case BUY -> 1;
            case SELL -> -1;
            case HOLD -> 0;
        };
    }

    static Instant latestTimestamp(List<WeightedSignal> signals) {
        return signals.stream()
                .map(weighted -> weighted.signal().at())
                .max(Instant::compareTo)
                .orElseThrow();
    }
}
