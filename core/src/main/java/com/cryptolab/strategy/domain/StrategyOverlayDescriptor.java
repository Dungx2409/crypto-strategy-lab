package com.cryptolab.strategy.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.Map;

public record StrategyOverlayDescriptor(
        String id, String kind, Map<String, Object> configuration) {

    public StrategyOverlayDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("overlay id must not be blank");
        }
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("overlay kind must not be blank");
        }
        configuration = ImmutableValues.copyMap(configuration);
    }
}
