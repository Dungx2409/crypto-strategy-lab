package com.cryptolab.strategy.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.Map;
import java.util.Locale;

public record StrategyDefinition(String type, String version, Map<String, Object> parameters) {

    public StrategyDefinition {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        type = type.trim().toUpperCase(Locale.ROOT);
        version = version.trim();
        parameters = ImmutableValues.copyMap(parameters);
    }
}
