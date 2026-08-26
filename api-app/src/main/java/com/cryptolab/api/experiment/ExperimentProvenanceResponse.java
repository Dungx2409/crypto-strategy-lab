package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.ExperimentProvenance;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentProvenanceResponse(
        UUID experimentId,
        UUID candidateId,
        UUID searchRunId,
        ExperimentStatus status,
        UUID reproductionOfExperimentId,
        String candidateHash,
        List<StrategyDefinition> strategies,
        CombinationPolicyDefinition combinationPolicy,
        DatasetReferenceResponse dataset,
        ExecutionConfig executionConfig,
        GeneratorSnapshot generator,
        String evaluatorVersion,
        String codeCommit,
        String buildVersion,
        Instant startedAt,
        Instant completedAt,
        EvaluationMetricsResponse metrics) {

    static ExperimentProvenanceResponse from(ExperimentProvenance provenance) {
        return new ExperimentProvenanceResponse(
                provenance.experimentId(),
                provenance.candidateId(),
                provenance.searchRunId(),
                provenance.status(),
                provenance.reproductionOfExperimentId(),
                provenance.candidateHash(),
                provenance.strategies(),
                provenance.combinationPolicy(),
                DatasetReferenceResponse.from(provenance.dataset()),
                provenance.executionConfig(),
                provenance.generator(),
                provenance.evaluatorVersion(),
                provenance.codeCommit(),
                provenance.buildVersion(),
                provenance.startedAt(),
                provenance.completedAt(),
                EvaluationMetricsResponse.from(
                        provenance.metrics(), provenance.executionConfig().initialCapital()));
    }
}
