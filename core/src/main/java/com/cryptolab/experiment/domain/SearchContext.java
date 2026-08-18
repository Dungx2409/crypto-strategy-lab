package com.cryptolab.experiment.domain;

import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record SearchContext(
        UUID searchRunId,
        MarketDatasetRef dataset,
        List<String> strategyTypes,
        Map<String, String> strategyVersions,
        SearchParameterSpace parameterSpace,
        CombinationPolicyDefinition combinationPolicy,
        long randomSeed,
        StopConditions stopConditions,
        int batchSize) {

    public SearchContext {
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        strategyTypes = Objects.requireNonNull(strategyTypes, "strategyTypes must not be null").stream()
                .map(type -> {
                    if (type == null || type.isBlank()) {
                        throw new IllegalArgumentException("strategy type must not be blank");
                    }
                    return type.trim().toUpperCase(Locale.ROOT);
                })
                .distinct()
                .sorted()
                .toList();
        if (strategyTypes.isEmpty()) {
            throw new IllegalArgumentException("strategyTypes must not be empty");
        }
        Map<String, String> versions = new LinkedHashMap<>();
        if (strategyVersions != null) {
            strategyVersions.forEach((type, version) -> {
                if (type == null || type.isBlank() || version == null || version.isBlank()) {
                    throw new IllegalArgumentException("strategyVersions must contain non-blank type/version pairs");
                }
                versions.put(type.trim().toUpperCase(Locale.ROOT), version.trim());
            });
        }
        if (!versions.keySet().equals(new java.util.HashSet<>(strategyTypes))) {
            throw new IllegalArgumentException("strategyVersions must define exactly the selected strategyTypes");
        }
        strategyVersions = Map.copyOf(versions);
        parameterSpace = parameterSpace == null ? new SearchParameterSpace(Map.of()) : parameterSpace;
        if (!strategyTypes.containsAll(parameterSpace.values().keySet())) {
            throw new IllegalArgumentException("parameterSpace contains an unselected strategy type");
        }
        Objects.requireNonNull(combinationPolicy, "combinationPolicy must not be null");
        if (!combinationPolicy.weights().isEmpty()
                && !combinationPolicy.weights().keySet().equals(new java.util.HashSet<>(strategyTypes))) {
            throw new IllegalArgumentException(
                    "non-empty combination weights must define exactly the selected strategyTypes");
        }
        Objects.requireNonNull(stopConditions, "stopConditions must not be null");
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
    }
}
