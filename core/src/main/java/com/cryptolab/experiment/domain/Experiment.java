package com.cryptolab.experiment.domain;

import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Experiment(
        UUID id,
        UUID candidateId,
        UUID searchRunId,
        ExperimentStatus status,
        MarketDatasetRef dataset,
        ExecutionConfig executionConfig,
        List<StrategyDefinition> strategySnapshot,
        CombinationPolicyDefinition combinationPolicy,
        String generatorType,
        String generatorVersion,
        Long randomSeed,
        String evaluatorVersion,
        String codeCommit,
        String buildVersion,
        UUID reproductionOfExperimentId,
        Instant startedAt,
        Instant completedAt,
        String failureCode,
        String failureMessage,
        long version) {

    public Experiment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(executionConfig, "executionConfig must not be null");
        strategySnapshot = List.copyOf(
                Objects.requireNonNull(strategySnapshot, "strategySnapshot must not be null"));
        Objects.requireNonNull(combinationPolicy, "combinationPolicy must not be null");
        if (generatorType == null || generatorType.isBlank()) {
            throw new IllegalArgumentException("generatorType must not be blank");
        }
        if (generatorVersion == null || generatorVersion.isBlank()) {
            throw new IllegalArgumentException("generatorVersion must not be blank");
        }
        if (evaluatorVersion == null || evaluatorVersion.isBlank()) {
            throw new IllegalArgumentException("evaluatorVersion must not be blank");
        }
        if (codeCommit == null || codeCommit.isBlank()) {
            throw new IllegalArgumentException("codeCommit must not be blank");
        }
        if (buildVersion == null || buildVersion.isBlank()) {
            throw new IllegalArgumentException("buildVersion must not be blank");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
