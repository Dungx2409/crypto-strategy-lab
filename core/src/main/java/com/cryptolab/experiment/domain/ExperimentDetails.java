package com.cryptolab.experiment.domain;

import java.util.List;
import java.util.Objects;

public record ExperimentDetails(
        Experiment experiment,
        CandidateStrategy candidate,
        GeneratorSnapshot generator,
        List<RecordedSignal> signals,
        List<Trade> trades,
        EvaluationMetrics metrics,
        Integer rank) {

    public ExperimentDetails {
        Objects.requireNonNull(experiment, "experiment must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(generator, "generator must not be null");
        signals = signals == null ? List.of() : List.copyOf(signals);
        trades = trades == null ? List.of() : List.copyOf(trades);
    }
}
