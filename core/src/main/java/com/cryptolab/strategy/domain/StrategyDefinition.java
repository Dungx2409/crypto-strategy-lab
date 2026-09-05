package com.cryptolab.strategy.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.Locale;
import java.util.Map;

public record StrategyDefinition(
        String type,
        String version,
        Map<String, Object> parameters,
        String displayLabel) {

    public StrategyDefinition(String type, String version, Map<String, Object> parameters) {
        this(type, version, parameters, null);
    }

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
        displayLabel = normalizeDisplayLabel(displayLabel);
    }

    private static String normalizeDisplayLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String label = value.trim().toUpperCase(Locale.ROOT);
        if (!label.matches("[A-Z0-9_]{2,16}")) {
            throw new IllegalArgumentException(
                    "strategy displayLabel must contain 2 to 16 uppercase letters, digits, or underscores");
        }
        return label;
    }
}
