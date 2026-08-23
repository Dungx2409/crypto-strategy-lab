package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetChecksum;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.TradeDirection;
import com.cryptolab.experiment.domain.TradeExitReason;
import com.cryptolab.shared.domain.SentimentObservation;
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
import java.util.UUID;
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
            assertThat(trade.direction()).isEqualTo(TradeDirection.LONG);
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
    void sellSignalOpensAndLiquidatesAShortPositionWhenEnabled() {
        CandidateStrategy candidate = ExperimentTestFixtures.candidate();
        MarketDataset dataset = ExperimentTestFixtures.dataset();
        StrategyRegistry alwaysSell = new SingleStrategyRegistry(context ->
                new Signal(SignalType.SELL, BigDecimal.ONE.negate(), context.evaluatedAt(), "sell"));
        DeterministicBacktestEngine engine = new DeterministicBacktestEngine(
                ignored -> candidate,
                ignored -> dataset,
                alwaysSell,
                definition -> new MajorityVotePolicy(),
                Clock.systemUTC());
        ExecutionConfig config = new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                true,
                DeterministicBacktestEngine.FILL_POLICY,
                DeterministicBacktestEngine.VERSION);

        BacktestResult result = engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                config));

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.direction()).isEqualTo(TradeDirection.SHORT);
            assertThat(trade.entryPrice()).isEqualByComparingTo("110");
            assertThat(trade.exitPrice()).isEqualByComparingTo("125");
            assertThat(trade.pnl()).isNegative();
        });
        assertThat(result.endingCapital()).isLessThan(config.initialCapital());
    }

    @Test
    void positionSizeLimitsCapitalCommittedToATrade() {
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
        ExecutionConfig config = new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                false,
                DeterministicBacktestEngine.FILL_POLICY,
                DeterministicBacktestEngine.VERSION,
                new BigDecimal("50"));

        BacktestResult result = engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                config));

        BigDecimal expectedQuantity = new BigDecimal("5000")
                .divide(new BigDecimal("110.11"), new java.math.MathContext(24, java.math.RoundingMode.HALF_UP));
        assertThat(result.trades()).singleElement().satisfies(trade ->
                assertThat(trade.quantity()).isEqualByComparingTo(expectedQuantity));
        assertThat(result.endingCapital()).isEqualByComparingTo(
                new BigDecimal("5000").add(expectedQuantity
                        .multiply(new BigDecimal("125"))
                        .multiply(new BigDecimal("0.999"))));
    }

    @Test
    void stopLossWinsWhenStopAndTargetAreTouchedInTheSameCandle() {
        CandidateStrategy candidate = ExperimentTestFixtures.candidate();
        MarketDataset dataset = ExperimentTestFixtures.dataset();
        StrategyRegistry buyOnce = new SingleStrategyRegistry(context -> context.candles().size() == 1
                ? new Signal(SignalType.BUY, BigDecimal.ONE, context.evaluatedAt(), "buy")
                : new Signal(SignalType.HOLD, BigDecimal.ZERO, context.evaluatedAt(), "hold"));
        DeterministicBacktestEngine engine = new DeterministicBacktestEngine(
                ignored -> candidate,
                ignored -> dataset,
                buyOnce,
                definition -> new MajorityVotePolicy(),
                Clock.systemUTC());
        ExecutionConfig config = new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                false,
                DeterministicBacktestEngine.FILL_POLICY,
                DeterministicBacktestEngine.VERSION,
                new BigDecimal("100"),
                new BigDecimal("0.5"),
                new BigDecimal("4"));

        BacktestResult result = engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                config));

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.exitReason()).isEqualTo(TradeExitReason.STOP_LOSS);
            assertThat(trade.exitPrice()).isEqualByComparingTo("109.45");
            assertThat(trade.exitTime()).isEqualTo(ExperimentTestFixtures.START.plusSeconds(600));
        });
    }

    @Test
    void takeProfitClosesAtTheConfiguredThreshold() {
        CandidateStrategy candidate = ExperimentTestFixtures.candidate();
        MarketDataset dataset = ExperimentTestFixtures.dataset();
        StrategyRegistry buyOnce = new SingleStrategyRegistry(context -> context.candles().size() == 1
                ? new Signal(SignalType.BUY, BigDecimal.ONE, context.evaluatedAt(), "buy")
                : new Signal(SignalType.HOLD, BigDecimal.ZERO, context.evaluatedAt(), "hold"));
        DeterministicBacktestEngine engine = new DeterministicBacktestEngine(
                ignored -> candidate,
                ignored -> dataset,
                buyOnce,
                definition -> new MajorityVotePolicy(),
                Clock.systemUTC());
        ExecutionConfig config = new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                false,
                DeterministicBacktestEngine.FILL_POLICY,
                DeterministicBacktestEngine.VERSION,
                new BigDecimal("100"),
                null,
                new BigDecimal("4"));

        BacktestResult result = engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                config));

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.exitReason()).isEqualTo(TradeExitReason.TAKE_PROFIT);
            assertThat(trade.exitPrice()).isEqualByComparingTo("114.4");
        });
    }

    @Test
    void trailingStopUsesTheHighWaterMarkFromCompletedCandles() {
        CandidateStrategy candidate = ExperimentTestFixtures.candidate();
        List<com.cryptolab.marketdata.domain.Candle> candles = List.of(
                ExperimentTestFixtures.candle(0, "100", "105"),
                ExperimentTestFixtures.candle(1, "110", "115"),
                ExperimentTestFixtures.candle(2, "115", "110"));
        MarketDatasetRef reference = new MarketDatasetRef(
                "BTCUSDT",
                com.cryptolab.marketdata.domain.Timeframe.M5,
                ExperimentTestFixtures.START,
                ExperimentTestFixtures.START.plusSeconds(900),
                "trailing-test-v1",
                MarketDatasetChecksum.calculate(candles));
        MarketDataset dataset = new MarketDataset(UUID.randomUUID(), reference, candles);
        StrategyRegistry buyOnce = new SingleStrategyRegistry(context -> context.candles().size() == 1
                ? new Signal(SignalType.BUY, BigDecimal.ONE, context.evaluatedAt(), "buy")
                : new Signal(SignalType.HOLD, BigDecimal.ZERO, context.evaluatedAt(), "hold"));
        DeterministicBacktestEngine engine = new DeterministicBacktestEngine(
                ignored -> candidate,
                ignored -> dataset,
                buyOnce,
                definition -> new MajorityVotePolicy(),
                Clock.systemUTC());
        ExecutionConfig config = new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                false,
                DeterministicBacktestEngine.FILL_POLICY,
                DeterministicBacktestEngine.VERSION,
                new BigDecimal("100"),
                null,
                null,
                new BigDecimal("5"));

        BacktestResult result = engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                config));

        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.exitReason()).isEqualTo(TradeExitReason.TRAILING_STOP);
            assertThat(trade.exitPrice()).isEqualByComparingTo("110.2");
        });
    }

    @Test
    void strategyContextNeverReceivesFutureSentimentObservations() {
        CandidateStrategy candidate = ExperimentTestFixtures.candidate();
        MarketDataset base = ExperimentTestFixtures.dataset();
        SentimentObservation observation = new SentimentObservation(
                "news-1",
                ExperimentTestFixtures.START.plusSeconds(600),
                new BigDecimal("0.8"),
                "keyword",
                "1.0",
                "news-v1",
                "normalize-v1");
        MarketDatasetRef reference = new MarketDatasetRef(
                base.reference().symbol(),
                base.reference().timeframe(),
                base.reference().from(),
                base.reference().to(),
                "sentiment-test-v1",
                MarketDatasetChecksum.calculate(base.candles(), List.of(observation)));
        MarketDataset dataset = new MarketDataset(
                UUID.randomUUID(), reference, base.candles(), List.of(observation));
        List<Integer> observedCounts = new ArrayList<>();
        StrategyRegistry registry = new SingleStrategyRegistry(context -> {
            observedCounts.add(context.sentimentObservations().size());
            return new Signal(SignalType.HOLD, BigDecimal.ZERO, context.evaluatedAt(), "hold");
        });
        DeterministicBacktestEngine engine = new DeterministicBacktestEngine(
                ignored -> candidate,
                ignored -> dataset,
                registry,
                definition -> new MajorityVotePolicy(),
                Clock.systemUTC());

        engine.run(new BacktestCommand(
                ExperimentTestFixtures.EXPERIMENT_ID,
                candidate.candidateId(),
                dataset.reference(),
                ExperimentTestFixtures.executionConfig()));

        assertThat(observedCounts).containsExactly(0, 1, 1);
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
