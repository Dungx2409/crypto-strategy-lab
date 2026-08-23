package com.cryptolab.strategy.domain;

import java.util.List;

public record UserStrategyDocument(
        String name,
        String description,
        List<StrategyDefinition> strategies,
        CombinationPolicyDefinition combinationPolicy) {

    public UserStrategyDocument {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("strategy name must contain 1 to 100 characters");
        }
        name = name.trim();
        description = description == null ? "" : description.trim();
        strategies = List.copyOf(strategies == null ? List.of() : strategies);
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("at least one strategy is required");
        }
        if (combinationPolicy == null) {
            throw new IllegalArgumentException("combinationPolicy must not be null");
        }
    }
}
