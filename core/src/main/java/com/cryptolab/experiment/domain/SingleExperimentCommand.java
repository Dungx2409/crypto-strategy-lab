package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record SingleExperimentCommand(
        String symbol,
        Timeframe timeframe,
        String datasetVersion,
        List<Candle> candles,
        List<StrategyDefinition> strategies,
        CombinationPolicyDefinition combinationPolicy,
        ExecutionConfig executionConfig,
        GeneratorSnapshot generator) {

    public SingleExperimentCommand {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        symbol = symbol.trim().toUpperCase(Locale.ROOT);
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw new IllegalArgumentException("datasetVersion must not be blank");
        }
        datasetVersion = datasetVersion.trim();
        candles = List.copyOf(Objects.requireNonNull(candles, "candles must not be null"));
        if (candles.size() < 2) {
            throw new IllegalArgumentException("a backtest dataset requires at least two candles");
        }
        strategies = List.copyOf(Objects.requireNonNull(strategies, "strategies must not be null"));
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies must not be empty");
        }
        Objects.requireNonNull(combinationPolicy, "combinationPolicy must not be null");
        Objects.requireNonNull(executionConfig, "executionConfig must not be null");
        Objects.requireNonNull(generator, "generator must not be null");
    }
}
