package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.Evaluation;
import com.cryptolab.experiment.domain.ExecutionConfig;
import java.time.Instant;

public interface ExperimentEvaluator {

    String version();

    Evaluation evaluate(BacktestResult result, ExecutionConfig executionConfig, Instant evaluatedAt);
}
