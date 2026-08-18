package com.cryptolab.experiment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ExperimentStateMachine {

    private static final Map<ExperimentStatus, Set<ExperimentStatus>> TRANSITIONS = Map.of(
            ExperimentStatus.CREATED, EnumSet.of(ExperimentStatus.QUEUED, ExperimentStatus.RUNNING, ExperimentStatus.CANCELLED),
            ExperimentStatus.QUEUED, EnumSet.of(ExperimentStatus.RUNNING, ExperimentStatus.CANCELLED, ExperimentStatus.FAILED),
            ExperimentStatus.RUNNING, EnumSet.of(
                    ExperimentStatus.RETRY_PENDING,
                    ExperimentStatus.COMPLETED,
                    ExperimentStatus.CANCELLED,
                    ExperimentStatus.FAILED),
            ExperimentStatus.RETRY_PENDING, EnumSet.of(
                    ExperimentStatus.QUEUED,
                    ExperimentStatus.CANCELLED,
                    ExperimentStatus.FAILED),
            ExperimentStatus.COMPLETED, EnumSet.noneOf(ExperimentStatus.class),
            ExperimentStatus.CANCELLED, EnumSet.noneOf(ExperimentStatus.class),
            ExperimentStatus.FAILED, EnumSet.noneOf(ExperimentStatus.class));

    private ExperimentStateMachine() {}

    public static void requireTransition(ExperimentStatus from, ExperimentStatus to) {
        if (from == null || to == null || !TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("invalid experiment transition: " + from + " -> " + to);
        }
    }
}
