package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.ExperimentDetails;
import com.cryptolab.experiment.domain.ExperimentStatus;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.experiment.domain.Trade;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentDetailsResponse(
        UUID experimentId,
        UUID candidateId,
        UUID searchRunId,
        ExperimentStatus status,
        Integer rank,
        String candidateHash,
        List<StrategyDefinition> strategies,
        CombinationPolicyDefinition combinationPolicy,
        DatasetReferenceResponse dataset,
        ExecutionConfig executionConfig,
        GeneratorSnapshot generator,
        String evaluatorVersion,
        String codeCommit,
        String buildVersion,
        UUID reproductionOfExperimentId,
        Instant startedAt,
        Instant completedAt,
        EvaluationMetricsResponse metrics,
        List<SignalResponse> signals,
        List<Trade> trades) {

    static ExperimentDetailsResponse from(ExperimentDetails details) {
        var experiment = details.experiment();
        return new ExperimentDetailsResponse(
                experiment.id(),
                experiment.candidateId(),
                experiment.searchRunId(),
                experiment.status(),
                details.rank(),
                details.candidate().candidateHash(),
                details.candidate().strategies(),
                details.candidate().combinationPolicy(),
                DatasetReferenceResponse.from(experiment.dataset()),
                experiment.executionConfig(),
                details.generator(),
                experiment.evaluatorVersion(),
                experiment.codeCommit(),
                experiment.buildVersion(),
                experiment.reproductionOfExperimentId(),
                experiment.startedAt(),
                experiment.completedAt(),
                EvaluationMetricsResponse.from(details.metrics()),
                details.signals().stream().map(SignalResponse::from).toList(),
                details.trades());
    }

    public record SignalResponse(
            String strategyType,
            String strategyVersion,
            com.cryptolab.strategy.domain.SignalType type,
            BigDecimal strength,
            Instant at,
            String reason) {

        private static SignalResponse from(com.cryptolab.experiment.domain.RecordedSignal recorded) {
            return new SignalResponse(
                    recorded.strategyType(),
                    recorded.strategyVersion(),
                    recorded.signal().type(),
                    recorded.signal().strength(),
                    recorded.signal().at(),
                    recorded.signal().reason());
        }
    }
}
