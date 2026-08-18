package com.cryptolab.experiment.domain;

import java.time.Duration;
import java.util.Optional;

public final class StopConditionEvaluator {

    public Optional<SearchStopReason> evaluate(StopConditions conditions, SearchProgress progress) {
        if (conditions.maxCandidates() != null
                && progress.generatedCandidates() >= conditions.maxCandidates()) {
            return Optional.of(SearchStopReason.MAX_CANDIDATES);
        }
        if (conditions.maxDuration() != null
                && Duration.between(progress.startedAt(), progress.observedAt())
                        .compareTo(conditions.maxDuration()) >= 0) {
            return Optional.of(SearchStopReason.MAX_DURATION);
        }
        if (conditions.noImprovementIterations() != null
                && progress.noImprovementIterations() >= conditions.noImprovementIterations()) {
            return Optional.of(SearchStopReason.NO_IMPROVEMENT);
        }
        return Optional.empty();
    }
}
