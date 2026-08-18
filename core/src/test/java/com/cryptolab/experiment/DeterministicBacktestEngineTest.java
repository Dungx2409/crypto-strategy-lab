package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import com.cryptolab.strategy.domain.policy.MajorityVotePolicy;
import com.cryptolab.strategy.port.StrategyFactory;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicBacktestEngineTest {

    @Test
    void signalsUseOnlyTheKnownPrefixAndFillOnTheNextCandleOpen() {
        CandidateStrategy candidate = ExperimentTestFixtures.candidate();
        MarketDataset dataset = ExperimentTestFixtures.dataset();
        PrefixAwareRegistry registry = new PrefixAwareRegistry();
        CombinationPolicyResolver policies = definition -> new MajorityVotePolicy();
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        DeterministicBacktestEngine engine = new DeterministicBacktestEngine(
                ignored -> candidate,
                ignored -> dataset,
                registry,
                policies,
                clock);

        BacktestResult result = engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                ExperimentTestFixtures.executionConfig()));

        assertThat(registry.observedContextSizes).containsExactly(1, 2, 3);
        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.entryTime()).isEqualTo(dataset.candles().get(1).openTime());
            assertThat(trade.entryPrice()).isEqualByComparingTo("110");
            assertThat(trade.exitTime()).isEqualTo(dataset.candles().get(2).openTime());
            assertThat(trade.exitPrice()).isEqualByComparingTo("120");
            BigDecimal expectedQuantity = new BigDecimal("10000")
                    .divide(new BigDecimal("110.11"), new java.math.MathContext(24, java.math.RoundingMode.HALF_UP));
            BigDecimal expectedFee = expectedQuantity.multiply(new BigDecimal("110"))
                    .multiply(new BigDecimal("0.001"))
                    .add(expectedQuantity.multiply(new BigDecimal("120"))
                            .multiply(new BigDecimal("0.001")));
            assertThat(trade.quantity()).isEqualByComparingTo(expectedQuantity);
            assertThat(trade.fee()).isEqualByComparingTo(expectedFee);
        });
        assertThat(result.endingCapital()).isEqualByComparingTo(
                result.trades().getFirst().quantity()
                        .multiply(new BigDecimal("120"))
                        .multiply(new BigDecimal("0.999")));
        assertThat(result.signals()).hasSize(6);
        assertThat(result.signals().subList(0, 2))
                .allSatisfy(recorded -> assertThat(recorded.signal().at())
                        .isEqualTo(dataset.candles().getFirst().openTime()
                                .plus(dataset.candles().getFirst().timeframe().duration())));
    }

    @Test
    void openPositionIsDeterministicallyLiquidatedAtTheFinalClose() {
        CandidateStrategy candidate = ExperimentTestFixtures.candidate();
        MarketDataset dataset = ExperimentTestFixtures.dataset();
        StrategyRegistry alwaysBuy = new SingleStrategyRegistry(context ->
                new Signal(SignalType.BUY, BigDecimal.ONE, context.evaluatedAt(), "buy"));
        DeterministicBacktestEngine engine = new DeterministicBacktestEngine(
                ignored -> candidate,
                ignored -> dataset,
                alwaysBuy,
                definition -> new MajorityVotePolicy(),
                Clock.systemUTC());

        BacktestResult result = engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                ExperimentTestFixtures.executionConfig()));

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.entryPrice()).isEqualByComparingTo("110");
            assertThat(trade.exitPrice()).isEqualByComparingTo("125");
            assertThat(trade.exitTime()).isEqualTo(ExperimentTestFixtures.START.plusSeconds(900));
        });
    }

    private static final class PrefixAwareRegistry implements StrategyRegistry {

        private final List<Integer> observedContextSizes = new ArrayList<>();

        @Override
        public Strategy create(StrategyDefinition definition) {
            return strategy(context -> {
                observedContextSizes.add(context.candles().size());
                SignalType type = context.candles().size() == 1
                        ? SignalType.BUY
                        : context.candles().size() == 2 ? SignalType.SELL : SignalType.HOLD;
                return signal(type, context);
            });
        }

        @Override
        public void register(StrategyFactory factory) {}

        @Override
        public Set<String> registeredTypes() {
            return Set.of("TEST");
        }

        @Override
        public List<com.cryptolab.strategy.domain.StrategyPluginDescriptor> availableStrategies() {
            return List.of();
        }
    }

    private static final class SingleStrategyRegistry implements StrategyRegistry {

        private final Analyzer analyzer;

        private SingleStrategyRegistry(Analyzer analyzer) {
            this.analyzer = analyzer;
        }

        @Override
        public Strategy create(StrategyDefinition definition) {
            return strategy(analyzer);
        }

        @Override
        public void register(StrategyFactory factory) {}

        @Override
        public Set<String> registeredTypes() {
            return Set.of("TEST");
        }

        @Override
        public List<com.cryptolab.strategy.domain.StrategyPluginDescriptor> availableStrategies() {
            return List.of();
        }
    }

    private static Strategy strategy(Analyzer analyzer) {
        return new Strategy() {
            @Override
            public StrategyDescriptor descriptor() {
                return new StrategyDescriptor("TEST", "1.0", Map.of());
            }

            @Override
            public Signal analyze(StrategyContext context) {
                return analyzer.analyze(context);
            }
        };
    }

    private static Signal signal(SignalType type, StrategyContext context) {
        BigDecimal strength = switch (type) {
            case BUY -> BigDecimal.ONE;
            case SELL -> BigDecimal.ONE.negate();
            case HOLD -> BigDecimal.ZERO;
        };
        return new Signal(type, strength, context.evaluatedAt(), "prefix test");
    }

    @FunctionalInterface
    private interface Analyzer {
        Signal analyze(StrategyContext context);
    }
}
