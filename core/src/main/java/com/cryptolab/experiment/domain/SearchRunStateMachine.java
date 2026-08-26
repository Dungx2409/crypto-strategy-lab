package com.cryptolab.experiment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class SearchRunStateMachine {

    private static final Map<SearchRunStatus, Set<SearchRunStatus>> TRANSITIONS = Map.of(
            SearchRunStatus.CREATED,
            EnumSet.of(SearchRunStatus.RUNNING, SearchRunStatus.CANCELLED, SearchRunStatus.FAILED),
            SearchRunStatus.RUNNING,
            EnumSet.of(
                    SearchRunStatus.EVALUATING,
                    SearchRunStatus.CANCELLED,
                    SearchRunStatus.FAILED),
            SearchRunStatus.EVALUATING,
            EnumSet.of(SearchRunStatus.COMPLETED, SearchRunStatus.CANCELLED, SearchRunStatus.FAILED),
            SearchRunStatus.COMPLETED,
            EnumSet.noneOf(SearchRunStatus.class),
            SearchRunStatus.CANCELLED,
            EnumSet.noneOf(SearchRunStatus.class),
            SearchRunStatus.FAILED,
            EnumSet.noneOf(SearchRunStatus.class));

    private SearchRunStateMachine() {}

    public static void requireTransition(SearchRunStatus from, SearchRunStatus to) {
        if (from == null || to == null || !TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("invalid search-run transition: " + from + " -> " + to);
        }
    }
}
