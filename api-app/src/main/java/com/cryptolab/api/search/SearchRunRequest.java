package com.cryptolab.api.search;

import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRunKind;
import com.cryptolab.experiment.domain.SearchStartCommand;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SearchRunRequest(
        String symbol,
        String timeframe,
        Instant from,
        Instant to,
        String datasetVersion,
        String datasetChecksum,
        List<String> strategyTypes,
        Map<String, String> strategyVersions,
        Map<String, Map<String, List<Object>>> parameterSpace,
        CombinationPolicyDefinition combinationPolicy,
        Long randomSeed,
        StopConditions stopConditions,
        Integer batchSize,
        ExecutionConfig executionConfig) {

    SearchStartCommand toCommand(UUID searchRunId, UUID ownerAccountId) {
        if (randomSeed == null) {
            throw new IllegalArgumentException("randomSeed must not be null");
        }
        Timeframe parsedTimeframe = Timeframe.fromExchangeCode(timeframe);
        MarketDatasetRef dataset = new MarketDatasetRef(
                symbol, parsedTimeframe, from, to, datasetVersion, datasetChecksum);
        SearchContext context = new SearchContext(
                searchRunId,
                dataset,
                strategyTypes,
                strategyVersions,
                new SearchParameterSpace(parameterSpace),
                combinationPolicy,
                randomSeed,
                stopConditions,
                batchSize == null ? 100 : batchSize);
        ExecutionConfig resolvedExecution = executionConfig == null
                ? new ExecutionConfig(
                        new BigDecimal("10000"),
                        new BigDecimal("0.001"),
                        false,
                        DeterministicBacktestEngine.FILL_POLICY,
                        DeterministicBacktestEngine.VERSION)
                : executionConfig;
        return new SearchStartCommand(
                context, resolvedExecution, ownerAccountId, SearchRunKind.SEARCH);
    }
}
