package com.cryptolab.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.GeneticStrategyGenerator;
import com.cryptolab.experiment.application.RandomStrategyGenerator;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.infrastructure.strategy.adapter.MovingAverageStrategyFactory;
import com.cryptolab.infrastructure.strategy.adapter.RsiStrategyFactory;
import com.cryptolab.infrastructure.strategy.adapter.SpringStrategyRegistry;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratorReplacementArchitectureTest {

    @Test
    void randomAndGeneticProduceTheSameCandidateContractBehindOnePort() {
        var registry = new SpringStrategyRegistry(List.of(new MovingAverageStrategyFactory()));
        var random = new RandomStrategyGenerator(registry);
        var genetic = new GeneticStrategyGenerator(registry);
        SearchContext context = context();

        var randomCandidate = random.generate(context).findFirst().orElseThrow();
        var geneticCandidate = genetic.generate(context).findFirst().orElseThrow();

        assertThat(random.type()).isEqualTo("random");
        assertThat(genetic.type()).isEqualTo("genetic");
        assertThat(randomCandidate.getClass()).isEqualTo(geneticCandidate.getClass());
        assertThat(randomCandidate.strategies()).isEqualTo(geneticCandidate.strategies());
        assertThat(randomCandidate.combinationPolicy()).isEqualTo(geneticCandidate.combinationPolicy());
    }

    @Test
    void replacementGeneratorsDiscoverNonEmptySubsetsOfSelectedFamilies() {
        var registry = new SpringStrategyRegistry(List.of(
                new MovingAverageStrategyFactory(), new RsiStrategyFactory()));
        SearchContext context = compositeContext();

        var randomMemberships = new RandomStrategyGenerator(registry)
                .generate(context)
                .map(candidate -> candidate.strategies().stream().map(StrategyDefinition::type).toList())
                .toList();
        var geneticMemberships = new GeneticStrategyGenerator(registry)
                .generate(context)
                .limit(20)
                .map(candidate -> candidate.strategies().stream().map(StrategyDefinition::type).toList())
                .toList();

        assertThat(randomMemberships).containsExactlyInAnyOrder(
                List.of("MA"), List.of("RSI"), List.of("MA", "RSI"));
        assertThat(geneticMemberships).contains(
                List.of("MA"), List.of("RSI"), List.of("MA", "RSI"));
        assertThat(geneticMemberships).allMatch(membership -> !membership.isEmpty());
    }

    private static SearchContext context() {
        UUID runId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        return new SearchContext(
                runId,
                new MarketDatasetRef(
                        "BTCUSDT",
                        Timeframe.M5,
                        Instant.parse("2026-08-18T00:00:00Z"),
                        Instant.parse("2026-08-18T01:00:00Z"),
                        "proof-v1",
                "proof-checksum"),
        List.of("MA"),
        Map.of("MA", "1.0"),
        Map.of(),
        new SearchParameterSpace(Map.of(
                "MA", Map.of("fastPeriod", List.of(10), "slowPeriod", List.of(20)))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                42L,
                new StopConditions(1L, null, null),
                1);
    }

    private static SearchContext compositeContext() {
        SearchContext single = context();
        return new SearchContext(
                single.searchRunId(),
        single.dataset(),
        List.of("MA", "RSI"),
        Map.of("MA", "1.0", "RSI", "1.0"),
        Map.of(),
        new SearchParameterSpace(Map.of(
                "MA", Map.of("fastPeriod", List.of(10), "slowPeriod", List.of(20)),
                        "RSI", Map.of(
                                "period", List.of(14),
                                "oversold", List.of(30),
                                "overbought", List.of(70)))),
                single.combinationPolicy(),
                single.randomSeed(),
                single.stopConditions(),
                single.batchSize());
    }
}
