package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManualRunRequest(
        UUID strategyId,
        String symbol,
        List<String> timeframes,
        Instant from,
        Instant to,
        ExecutionConfig executionConfig) {

    List<Timeframe> parsedTimeframes() {
        return timeframes == null
                ? List.of()
                : timeframes.stream().map(Timeframe::fromExchangeCode).toList();
    }

    ExecutionConfig resolvedExecutionConfig() {
        return executionConfig == null
                ? new ExecutionConfig(
                        new BigDecimal("10000"),
                        new BigDecimal("0.001"),
                        false,
                        DeterministicBacktestEngine.FILL_POLICY,
                        DeterministicBacktestEngine.VERSION)
                : executionConfig;
    }
}
