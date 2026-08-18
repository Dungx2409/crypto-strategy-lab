package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import java.util.UUID;

@FunctionalInterface
public interface BacktestJobProcessor {

    BacktestWorkerOutcome process(UUID experimentId, String workerId);
}
