package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.EquityPoint;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.ExperimentStateMachine;
import com.cryptolab.experiment.domain.ExperimentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationRankingStateTest {

    @Test
    void evaluatorCalculatesReturnDrawdownAndVersionedScore() {
        BacktestResult result = new BacktestResult(
                ExperimentTestFixtures.EXPERIMENT_ID,
                ExperimentTestFixtures.CANDIDATE_ID,
                List.of(),
                List.of(),
                List.of(
                        equity("10000", 0),
                        equity("12000", 1),
                        equity("9000", 2),
                        equity("11000", 3)),
                new BigDecimal("11000"),
                ExperimentTestFixtures.START,
                ExperimentTestFixtures.START.plusSeconds(1),
                "engine-v1");

        Evaluation evaluation = new DefaultExperimentEvaluator().evaluate(
                result,
                ExperimentTestFixtures.executionConfig(),
                ExperimentTestFixtures.START.plusSeconds(2));

        assertThat(evaluation.metrics().totalReturnPct()).isEqualByComparingTo("10");
        assertThat(evaluation.metrics().maxDrawdownPct()).isEqualByComparingTo("-25");
        assertThat(evaluation.metrics().score()).isEqualByComparingTo("-2.5");
        assertThat(evaluation.evaluatorVersion()).isEqualTo(DefaultExperimentEvaluator.VERSION);
    }

    @Test
    void rankingUsesAllSpecifiedDeterministicTieBreakers() {
        UUID lexicallyFirst = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID lexicallySecond = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Evaluation first = evaluation(lexicallyFirst, "5", "8", "-6");
        Evaluation second = evaluation(lexicallySecond, "5", "8", "-6");
        Evaluation higherReturn = evaluation(UUID.randomUUID(), "5", "9", "-8");
        Evaluation higherScore = evaluation(UUID.randomUUID(), "6", "1", "-20");

        assertThat(new DefaultRankingService().rank(List.of(second, first, higherReturn, higherScore)))
                .extracting(ranking -> ranking.experimentId())
                .containsExactly(higherScore.experimentId(), higherReturn.experimentId(), lexicallyFirst, lexicallySecond);
    }

    @Test
    void stateMachineAllowsLifecycleButRejectsCompletedMutation() {
        ExperimentStateMachine.requireTransition(ExperimentStatus.CREATED, ExperimentStatus.RUNNING);
        ExperimentStateMachine.requireTransition(ExperimentStatus.RUNNING, ExperimentStatus.RETRY_PENDING);
        ExperimentStateMachine.requireTransition(ExperimentStatus.RETRY_PENDING, ExperimentStatus.QUEUED);
        ExperimentStateMachine.requireTransition(ExperimentStatus.QUEUED, ExperimentStatus.RUNNING);
        ExperimentStateMachine.requireTransition(ExperimentStatus.RUNNING, ExperimentStatus.COMPLETED);

        assertThatThrownBy(() -> ExperimentStateMachine.requireTransition(
                        ExperimentStatus.COMPLETED, ExperimentStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED -> RUNNING");
    }

    private static EquityPoint equity(String value, int minute) {
        return new EquityPoint(ExperimentTestFixtures.START.plusSeconds(minute * 60L), new BigDecimal(value));
    }

    private static Evaluation evaluation(UUID id, String score, String returns, String drawdown) {
        return new Evaluation(
                id,
                new EvaluationMetrics(
                        new BigDecimal(returns), new BigDecimal(drawdown), 1, new BigDecimal(score)),
                DefaultExperimentEvaluator.VERSION,
                Instant.EPOCH);
    }
}
