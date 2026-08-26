package com.cryptolab.strategy.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.Locale;
import java.util.Map;
import java.util.List;

public record StrategyPluginDescriptor(
        String type,
        String version,
        Map<String, Object> parameterSchema,
        List<StrategyOverlayDescriptor> overlays) {

    public StrategyPluginDescriptor(
            String type, String version, Map<String, Object> parameterSchema) {
        this(type, version, parameterSchema, List.of());
    }

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
        overlays = List.copyOf(overlays == null ? List.of() : overlays);
    }
}
