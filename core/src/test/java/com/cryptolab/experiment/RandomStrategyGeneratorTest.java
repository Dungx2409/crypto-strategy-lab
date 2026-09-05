package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.RandomStrategyGenerator;
import com.cryptolab.experiment.application.GeneticStrategyGenerator;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.port.StrategyFactory;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RandomStrategyGeneratorTest {

    @Test
    void sameSeedVersionsAndParameterSpaceProduceTheSameLazySequence() {
        CountingRegistry firstRegistry = new CountingRegistry();
        CountingRegistry secondRegistry = new CountingRegistry();
        RandomStrategyGenerator first = new RandomStrategyGenerator(firstRegistry);
        RandomStrategyGenerator second = new RandomStrategyGenerator(secondRegistry);
        SearchContext context = context(41L);

        var firstStream = first.generate(context);

        assertThat(firstRegistry.created.get()).isZero();
        var firstCandidates = firstStream.limit(6).toList();
        var secondCandidates = second.generate(context).limit(6).toList();
        assertThat(firstCandidates).isEqualTo(secondCandidates);
        assertThat(firstCandidates).extracting(candidate -> candidate.candidateHash()).doesNotHaveDuplicates();
        assertThat(firstRegistry.created).hasValue(
                firstCandidates.stream().mapToInt(candidate -> candidate.strategies().size()).sum());
    }

    @Test
    void differentSeedChangesOrderWithoutChangingTheFiniteCandidateSet() {
        RandomStrategyGenerator generator = new RandomStrategyGenerator(new CountingRegistry());

        var first = generator.generate(context(10L)).toList();
        var second = generator.generate(context(11L)).toList();

        assertThat(first).extracting(candidate -> candidate.candidateHash())
                .containsExactlyInAnyOrderElementsOf(second.stream().map(candidate -> candidate.candidateHash()).toList());
        assertThat(first).extracting(candidate -> candidate.candidateHash())
                .isNotEqualTo(second.stream().map(candidate -> candidate.candidateHash()).toList());
        assertThat(first).hasSize(14);
        var memberships = first.stream()
                .map(candidate -> candidate.strategies().stream().map(StrategyDefinition::type).toList())
                .toList();
        assertThat(memberships).contains(List.of("MA"), List.of("RSI"), List.of("MA", "RSI"));
        assertThat(memberships).filteredOn(List.of("MA")::equals).hasSize(4);
        assertThat(memberships).filteredOn(List.of("RSI")::equals).hasSize(2);
        assertThat(memberships).filteredOn(List.of("MA", "RSI")::equals).hasSize(8);
    }

    @Test
    void geneticGeneratorIsLazyDeterministicAndProducesValidCandidatesAcrossGenerations() {
        CountingRegistry firstRegistry = new CountingRegistry();
        CountingRegistry secondRegistry = new CountingRegistry();
        GeneticStrategyGenerator first = new GeneticStrategyGenerator(firstRegistry);
        GeneticStrategyGenerator second = new GeneticStrategyGenerator(secondRegistry);

        var stream = first.generate(context(73L));

        assertThat(firstRegistry.created).hasValue(0);
        var firstSequence = stream.limit(24).toList();
        var secondSequence = second.generate(context(73L)).limit(24).toList();
        assertThat(firstSequence).isEqualTo(secondSequence);
        assertThat(firstSequence).allSatisfy(candidate -> {
            assertThat(candidate.strategies()).isNotEmpty().hasSizeLessThanOrEqualTo(2);
            assertThat(candidate.candidateHash()).isNotBlank();
        });
        assertThat(firstSequence.stream()
                        .map(candidate -> candidate.strategies().stream()
                                .map(StrategyDefinition::type)
                                .toList()))
                .contains(List.of("MA"), List.of("RSI"), List.of("MA", "RSI"));
        assertThat(first.type()).isEqualTo("genetic");
        assertThat(first.version()).isEqualTo("2.0");
    }

    @Test
    void geneticParentSelectionChangesWithEvaluatedFitness() {
        SearchContext context = context(73L);
        GeneticStrategyGenerator generator = new GeneticStrategyGenerator(new CountingRegistry());
        var firstFavored = generator.generate(context, (runId, candidateIds) -> {
                    Map<UUID, BigDecimal> scores = new java.util.HashMap<>();
                    for (int index = 0; index < candidateIds.size(); index++) {
                        scores.put(candidateIds.get(index), BigDecimal.valueOf(candidateIds.size() - index));
                    }
                    return scores;
                })
                .limit(28)
                .toList();
        var lastFavored = generator.generate(context, (runId, candidateIds) -> {
                    Map<UUID, BigDecimal> scores = new java.util.HashMap<>();
                    for (int index = 0; index < candidateIds.size(); index++) {
                        scores.put(candidateIds.get(index), BigDecimal.valueOf(index));
                    }
                    return scores;
                })
                .limit(28)
                .toList();

        assertThat(firstFavored.subList(0, 14)).isEqualTo(lastFavored.subList(0, 14));
        assertThat(firstFavored.subList(14, 28)).isNotEqualTo(lastFavored.subList(14, 28));
    }

    private static SearchContext context(long seed) {
        return new SearchContext(
                ExperimentTestFixtures.EXPERIMENT_ID,
                ExperimentTestFixtures.dataset().reference(),
                List.of("RSI", "MA"),
                Map.of("MA", "1.0", "RSI", "1.0"),
                Map.of(),
                new SearchParameterSpace(Map.of(
                        "MA", Map.of(
                                "fastPeriod", List.of(10, 20),
                                "slowPeriod", List.of(50, 100)),
                        "RSI", Map.of(
                                "period", List.of(14, 21),
                                "oversold", List.of(30),
                                "overbought", List.of(70)))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                seed,
                new StopConditions(100L, null, null),
                20);
    }

    private static final class CountingRegistry implements StrategyRegistry {

        private final AtomicInteger created = new AtomicInteger();

        @Override
        public Strategy create(StrategyDefinition definition) {
            created.incrementAndGet();
            return null;
        }

        @Override
        public void register(StrategyFactory factory) {}

        @Override
        public Set<String> registeredTypes() {
            return Set.of("MA", "RSI");
        }

        @Override
        public List<StrategyPluginDescriptor> availableStrategies() {
            return List.of(
                    new StrategyPluginDescriptor("MA", "1.0", Map.of(
                            "fastPeriod", Map.of("default", 10),
                            "slowPeriod", Map.of("default", 20))),
                    new StrategyPluginDescriptor("RSI", "1.0", Map.of(
                            "period", Map.of("default", 14),
                            "oversold", Map.of("default", 30),
                            "overbought", Map.of("default", 70))));
        }
    }
}
