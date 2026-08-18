package com.cryptolab.experiment;

import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetChecksum;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ExperimentTestFixtures {

    static final UUID EXPERIMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    static final UUID CANDIDATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    static final UUID DATASET_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    static final Instant START = Instant.parse("2026-08-18T00:00:00Z");

    private ExperimentTestFixtures() {}

    static CandidateStrategy candidate() {
        List<StrategyDefinition> strategies = List.of(new StrategyDefinition("TEST", "1.0", Map.of()));
        CombinationPolicyDefinition policy = new CombinationPolicyDefinition(
                "MAJORITY", "1.0", Map.of(), BigDecimal.ZERO);
        return new CandidateStrategy(
                CANDIDATE_ID,
                strategies,
                policy,
                CandidateCanonicalizer.hash(strategies, policy));
    }

    static MarketDataset dataset() {
        List<Candle> candles = List.of(
                candle(0, "100", "105"),
                candle(1, "110", "115"),
                candle(2, "120", "125"));
        MarketDatasetRef reference = new MarketDatasetRef(
                "BTCUSDT",
                Timeframe.M5,
                START,
                START.plusSeconds(900),
                "fixture-v1",
                MarketDatasetChecksum.calculate(candles));
        return new MarketDataset(DATASET_ID, reference, candles);
    }

    static ExecutionConfig executionConfig() {
        return new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                false,
                DeterministicBacktestEngine.FILL_POLICY,
                DeterministicBacktestEngine.VERSION);
    }

    static Candle candle(int index, String open, String close) {
        BigDecimal openValue = new BigDecimal(open);
        BigDecimal closeValue = new BigDecimal(close);
        return new Candle(
                "BTCUSDT",
                Timeframe.M5,
                START.plusSeconds(index * 300L),
                openValue,
                openValue.max(closeValue).add(BigDecimal.ONE),
                openValue.min(closeValue).subtract(BigDecimal.ONE),
                closeValue,
                BigDecimal.TEN);
    }
}
