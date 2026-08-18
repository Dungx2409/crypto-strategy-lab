package com.cryptolab.strategy.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.Map;

public record StrategyDescriptor(String type, String version, Map<String, Object> parameters) {

    public StrategyDescriptor {
        type = requireText(type, "type");
        version = requireText(version, "version");
        parameters = ImmutableValues.copyMap(parameters);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
