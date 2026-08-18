package com.cryptolab.strategy.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record CombinationPolicyDefinition(
        String type,
        String version,
        Map<String, BigDecimal> weights,
        BigDecimal threshold) {

    public CombinationPolicyDefinition {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        type = type.trim().toUpperCase(Locale.ROOT);
        version = version.trim();
        LinkedHashMap<String, BigDecimal> normalizedWeights = new LinkedHashMap<>();
        if (weights != null) {
            weights.forEach((strategyType, weight) -> {
                if (strategyType == null || strategyType.isBlank()) {
                    throw new IllegalArgumentException("weight strategy type must not be blank");
                }
                if (weight == null || weight.signum() < 0) {
                    throw new IllegalArgumentException("strategy weights must be non-negative");
                }
                normalizedWeights.put(strategyType.trim().toUpperCase(Locale.ROOT), weight);
            });
        }
        weights = Map.copyOf(normalizedWeights);
    }
}
