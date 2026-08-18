package com.cryptolab.experiment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExperimentPlan(
        UUID experimentId,
        UUID searchRunId,
        CandidateStrategy candidate,
        MarketDataset dataset,
        ExecutionConfig executionConfig,
        GeneratorSnapshot generator,
        String evaluatorVersion,
        String codeCommit,
        String buildVersion,
        UUID reproductionOfExperimentId,
        Instant createdAt) {

    public ExperimentPlan {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(searchRunId, "searchRunId must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(executionConfig, "executionConfig must not be null");
        Objects.requireNonNull(generator, "generator must not be null");
        if (evaluatorVersion == null || evaluatorVersion.isBlank()) {
            throw new IllegalArgumentException("evaluatorVersion must not be blank");
        }
        if (codeCommit == null || codeCommit.isBlank()) {
            throw new IllegalArgumentException("codeCommit must not be blank");
        }
        if (buildVersion == null || buildVersion.isBlank()) {
            throw new IllegalArgumentException("buildVersion must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public ExperimentPlan reproduceAs(UUID newExperimentId, Instant newCreatedAt) {
        return new ExperimentPlan(
                newExperimentId,
                searchRunId,
                candidate,
                dataset,
                executionConfig,
                generator,
                evaluatorVersion,
                codeCommit,
                buildVersion,
                experimentId,
                newCreatedAt);
    }
}
