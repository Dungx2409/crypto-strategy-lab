package com.cryptolab.strategy.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.Locale;
import java.util.Map;

public record StrategyPluginDescriptor(
        String type,
        String version,
        Map<String, Object> parameterSchema) {

    public StrategyPluginDescriptor {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        type = type.trim().toUpperCase(Locale.ROOT);
        version = version.trim();
        parameterSchema = ImmutableValues.copyMap(parameterSchema);
    }
}
