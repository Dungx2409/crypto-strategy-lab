package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestResult;

public interface BacktestPort {
    BacktestResult run(BacktestCommand command);
}
