package com.cryptolab.experiment.domain;

import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CandidateStrategy(
        UUID candidateId,
        List<StrategyDefinition> strategies,
        CombinationPolicyDefinition combinationPolicy,
        String candidateHash) {

    public CandidateStrategy {
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        strategies = List.copyOf(Objects.requireNonNull(strategies, "strategies must not be null"));
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("candidate must contain at least one strategy");
        }
        Objects.requireNonNull(combinationPolicy, "combinationPolicy must not be null");
        if (candidateHash == null || candidateHash.isBlank()) {
            throw new IllegalArgumentException("candidateHash must not be blank");
        }
    }
}
