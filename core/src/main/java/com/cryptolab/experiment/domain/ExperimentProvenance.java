package com.cryptolab.experiment.domain;

import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentProvenance(
        UUID experimentId,
        UUID candidateId,
        UUID searchRunId,
        ExperimentStatus status,
        UUID reproductionOfExperimentId,
        String candidateHash,
        List<StrategyDefinition> strategies,
        CombinationPolicyDefinition combinationPolicy,
        MarketDatasetRef dataset,
        ExecutionConfig executionConfig,
        GeneratorSnapshot generator,
        String evaluatorVersion,
        String codeCommit,
        String buildVersion,
        Instant startedAt,
        Instant completedAt,
        EvaluationMetrics metrics) {

    public static ExperimentProvenance from(ExperimentDetails details) {
        Experiment experiment = details.experiment();
        return new ExperimentProvenance(
                experiment.id(),
                experiment.candidateId(),
                experiment.searchRunId(),
                experiment.status(),
                experiment.reproductionOfExperimentId(),
                details.candidate().candidateHash(),
                details.candidate().strategies(),
                details.candidate().combinationPolicy(),
                experiment.dataset(),
                experiment.executionConfig(),
                details.generator(),
                experiment.evaluatorVersion(),
                experiment.codeCommit(),
                experiment.buildVersion(),
                experiment.startedAt(),
                experiment.completedAt(),
                details.metrics());
    }
}
