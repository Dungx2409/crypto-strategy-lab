package com.cryptolab.worker;

import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import java.time.Duration;

public interface WorkerTelemetry {

    void record(BacktestWorkerOutcome outcome);

    void recordDuration(Duration duration);

    void recordPoisonMessage();

    void recordInfrastructureFailure();

    static WorkerTelemetry noop() {
        return new WorkerTelemetry() {
            @Override
            public void record(BacktestWorkerOutcome outcome) {}

            @Override
            public void recordDuration(Duration duration) {}

            @Override
            public void recordPoisonMessage() {}

            @Override
            public void recordInfrastructureFailure() {}
        };
    }
}
