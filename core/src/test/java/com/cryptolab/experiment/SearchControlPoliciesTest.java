package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.experiment.domain.SearchProgress;
import com.cryptolab.experiment.domain.SearchRunStateMachine;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditionEvaluator;
import com.cryptolab.experiment.domain.StopConditions;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SearchControlPoliciesTest {

    private static final Instant START = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void evaluatesEverySupportedAutomaticStopConditionDeterministically() {
        StopConditionEvaluator evaluator = new StopConditionEvaluator();

        assertThat(evaluator.evaluate(
                        new StopConditions(5L, null, null), progress(5, 0, Duration.ZERO)))
                .contains(SearchStopReason.MAX_CANDIDATES);
        assertThat(evaluator.evaluate(
                        new StopConditions(null, Duration.ofSeconds(10), null),
                        progress(0, 0, Duration.ofSeconds(10))))
                .contains(SearchStopReason.MAX_DURATION);
        assertThat(evaluator.evaluate(
                        new StopConditions(null, null, 3), progress(0, 3, Duration.ZERO)))
                .contains(SearchStopReason.NO_IMPROVEMENT);
    }

    @Test
    void stateMachineAllowsCancellationButProtectsTerminalRuns() {
        SearchRunStateMachine.requireTransition(SearchRunStatus.CREATED, SearchRunStatus.RUNNING);
        SearchRunStateMachine.requireTransition(SearchRunStatus.RUNNING, SearchRunStatus.EVALUATING);
        SearchRunStateMachine.requireTransition(SearchRunStatus.EVALUATING, SearchRunStatus.CANCELLED);
        SearchRunStateMachine.requireTransition(SearchRunStatus.EVALUATING, SearchRunStatus.COMPLETED);

        assertThatThrownBy(() -> SearchRunStateMachine.requireTransition(
                        SearchRunStatus.RUNNING, SearchRunStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING -> COMPLETED");

        assertThatThrownBy(() -> SearchRunStateMachine.requireTransition(
                        SearchRunStatus.COMPLETED, SearchRunStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED -> RUNNING");
    }

    private static SearchProgress progress(long generated, int noImprovement, Duration elapsed) {
        return new SearchProgress(START, START.plus(elapsed), generated, generated, null, noImprovement);
    }
}
