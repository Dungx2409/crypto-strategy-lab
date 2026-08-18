package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.GeneratorSnapshot;
import com.cryptolab.experiment.domain.SingleExperimentCommand;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SingleExperimentRequest(
        String symbol,
        String timeframe,
        String datasetVersion,
        List<ExperimentCandleRequest> candles,
        List<StrategyDefinition> strategies,
        CombinationPolicyDefinition combinationPolicy,
        ExecutionConfig executionConfig,
        GeneratorSnapshot generator) {

    SingleExperimentCommand toCommand() {
        Timeframe parsedTimeframe = Timeframe.fromExchangeCode(timeframe);
        List<ExperimentCandleRequest> safeCandles = candles == null ? List.of() : List.copyOf(candles);
        ExecutionConfig resolvedExecution = executionConfig == null
                ? new ExecutionConfig(
                        new BigDecimal("10000"),
                        new BigDecimal("0.001"),
                        false,
                        DeterministicBacktestEngine.FILL_POLICY,
                        DeterministicBacktestEngine.VERSION)
                : executionConfig;
        GeneratorSnapshot resolvedGenerator = generator == null
                ? new GeneratorSnapshot(
                        "manual", "1.0", Map.of("mode", "single-candidate-m4"), null)
                : generator;
        return new SingleExperimentCommand(
                symbol,
                parsedTimeframe,
                datasetVersion,
                safeCandles.stream().map(candle -> candle.toDomain(symbol, parsedTimeframe)).toList(),
                strategies,
                combinationPolicy,
                resolvedExecution,
                resolvedGenerator);
    }
}
