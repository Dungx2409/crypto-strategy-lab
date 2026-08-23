package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.EquityPoint;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.RecordedSignal;
import com.cryptolab.experiment.domain.Trade;
import com.cryptolab.experiment.domain.TradeDirection;
import com.cryptolab.experiment.domain.TradeExitReason;
import com.cryptolab.experiment.port.BacktestPort;
import com.cryptolab.experiment.port.CandidateProvider;
import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.experiment.port.MarketDatasetProvider;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.strategy.domain.CombinationPolicy;
import com.cryptolab.strategy.domain.CombinedSignal;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.WeightedSignal;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DeterministicBacktestEngine implements BacktestPort {

    public static final String VERSION = "deterministic-next-open-v5";
    public static final String RISK_EXIT_VERSION = "deterministic-next-open-v4";
    public static final String POSITION_SIZE_VERSION = "deterministic-next-open-v3";
    public static final String SHORT_VERSION = "deterministic-next-open-v2";
    public static final String LEGACY_VERSION = "deterministic-next-open-v1";
    public static final String FILL_POLICY = "NEXT_CANDLE_OPEN";
    private static final MathContext MATH_CONTEXT = new MathContext(24, RoundingMode.HALF_UP);

    private final CandidateProvider candidateProvider;
    private final MarketDatasetProvider datasetProvider;
    private final StrategyRegistry strategyRegistry;
    private final CombinationPolicyResolver policyResolver;
    private final Clock clock;

    public DeterministicBacktestEngine(
            CandidateProvider candidateProvider,
            MarketDatasetProvider datasetProvider,
            StrategyRegistry strategyRegistry,
            CombinationPolicyResolver policyResolver,
            Clock clock) {
        this.candidateProvider = candidateProvider;
        this.datasetProvider = datasetProvider;
        this.strategyRegistry = strategyRegistry;
        this.policyResolver = policyResolver;
        this.clock = clock;
    }

    @Override
    public BacktestResult run(BacktestCommand command) {
        validateExecution(command.executionConfig());
        CandidateStrategy candidate = candidateProvider.getCandidate(command.candidateId());
        MarketDataset dataset = datasetProvider.getDataset(command.dataset());
        List<Strategy> strategies = candidate.strategies().stream()
                .map(strategyRegistry::create)
                .toList();
        CombinationPolicy policy = policyResolver.resolve(candidate.combinationPolicy());
        List<Candle> candles = dataset.candles();
        Portfolio portfolio = new Portfolio(command.executionConfig());
        List<RecordedSignal> recordedSignals = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>();
        SignalType pendingSignal = null;
        Instant startedAt = clock.instant();

        for (int index = 0; index < candles.size(); index++) {
            Candle candle = candles.get(index);
            Instant candleCloseTime = candle.openTime().plus(candle.timeframe().duration());
            if (pendingSignal != null) {
                portfolio.execute(pendingSignal, candle.openTime(), candle.open());
            }
            portfolio.applyRiskExits(candle, candleCloseTime);

            StrategyContext context = new StrategyContext(
                    new TradingPair(candle.symbol()),
                    candle.timeframe(),
                    candles.subList(0, index + 1),
                    candleCloseTime,
                    dataset.sentimentObservations().stream()
                            .filter(observation -> !observation.observedAt().isAfter(candleCloseTime))
                            .toList());
            List<WeightedSignal> weightedSignals = new ArrayList<>();
            for (Strategy strategy : strategies) {
                Signal signal = strategy.analyze(context);
                recordedSignals.add(new RecordedSignal(
                        strategy.descriptor().type(), strategy.descriptor().version(), signal));
                weightedSignals.add(new WeightedSignal(
                        strategy.descriptor(),
                        signal,
                        weightFor(candidate.combinationPolicy().weights(), strategy.descriptor().type())));
            }
            CombinedSignal combined = policy.combine(weightedSignals);
            recordedSignals.add(new RecordedSignal(
                    "COMPOSITE",
                    candidate.combinationPolicy().version(),
                    new Signal(
                            combined.type(),
                            normalizedStrength(combined.score()),
                            combined.at(),
                            "combined " + candidate.combinationPolicy().type() + " score=" + combined.score())));
            pendingSignal = combined.type();

            if (index == candles.size() - 1) {
                portfolio.liquidateAtDatasetEnd(candleCloseTime, candle.close());
            }
            equityCurve.add(new EquityPoint(candleCloseTime, portfolio.equityAt(candle.close())));
        }

        return new BacktestResult(
                command.experimentId(),
                command.candidateId(),
                portfolio.trades(),
                recordedSignals,
                equityCurve,
                portfolio.cash(),
                startedAt,
                clock.instant(),
                VERSION);
    }

    private static void validateExecution(ExecutionConfig config) {
        if (!FILL_POLICY.equals(config.fillPolicy())) {
            throw new IllegalArgumentException("unsupported fill policy: " + config.fillPolicy());
        }
        if (!VERSION.equals(config.engineVersion())
                && !RISK_EXIT_VERSION.equals(config.engineVersion())
                && !SHORT_VERSION.equals(config.engineVersion())
                && !POSITION_SIZE_VERSION.equals(config.engineVersion())
                && !LEGACY_VERSION.equals(config.engineVersion())) {
            throw new IllegalArgumentException("unsupported engine version: " + config.engineVersion());
        }
        if (LEGACY_VERSION.equals(config.engineVersion()) && config.allowShort()) {
            throw new IllegalArgumentException("short selling is not supported by engine version " + LEGACY_VERSION);
        }
        if (!VERSION.equals(config.engineVersion())
                && !RISK_EXIT_VERSION.equals(config.engineVersion())
                && !POSITION_SIZE_VERSION.equals(config.engineVersion())
                && config.positionSizePct().compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException(
                    "position sizing is not supported by engine version " + config.engineVersion());
        }
        if (!VERSION.equals(config.engineVersion())
                && !RISK_EXIT_VERSION.equals(config.engineVersion())
                && (config.stopLossPct() != null || config.takeProfitPct() != null)) {
            throw new IllegalArgumentException(
                    "risk exits are not supported by engine version " + config.engineVersion());
        }
        if (!VERSION.equals(config.engineVersion()) && config.trailingStopPct() != null) {
            throw new IllegalArgumentException(
                    "trailing stop is not supported by engine version " + config.engineVersion());
        }
    }

    private static BigDecimal weightFor(Map<String, BigDecimal> weights, String strategyType) {
        if (weights.isEmpty()) {
            return BigDecimal.ONE;
        }
        BigDecimal weight = weights.get(strategyType);
        if (weight == null) {
            throw new IllegalArgumentException("missing combination weight for strategy " + strategyType);
        }
        return weight;
    }

    private static BigDecimal normalizedStrength(BigDecimal score) {
        if (score.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return score.abs().min(BigDecimal.ONE).multiply(BigDecimal.valueOf(score.signum()));
    }

    private static final class Portfolio {

        private final ExecutionConfig config;
        private final List<Trade> trades = new ArrayList<>();
        private BigDecimal cash;
        private Position position;

        private Portfolio(ExecutionConfig config) {
            this.config = config;
            cash = config.initialCapital();
        }

        private void execute(SignalType signal, Instant at, BigDecimal price) {
            if (signal == SignalType.BUY && position == null) {
                open(TradeDirection.LONG, at, price);
            } else if (signal == SignalType.SELL && position == null && config.allowShort()) {
                open(TradeDirection.SHORT, at, price);
            } else if (signal == SignalType.SELL
                    && position != null
                    && position.direction() == TradeDirection.LONG) {
                close(at, price, TradeExitReason.SIGNAL);
            } else if (signal == SignalType.BUY
                    && position != null
                    && position.direction() == TradeDirection.SHORT) {
                close(at, price, TradeExitReason.SIGNAL);
            }
        }

        private void applyRiskExits(Candle candle, Instant exitTime) {
            if (position == null) {
                return;
            }
            BigDecimal hundred = new BigDecimal("100");
            BigDecimal stop = config.stopLossPct();
            if (stop != null) {
                BigDecimal factor = stop.divide(hundred, MATH_CONTEXT);
                BigDecimal level = position.direction() == TradeDirection.LONG
                        ? position.entryPrice().multiply(BigDecimal.ONE.subtract(factor))
                        : position.entryPrice().multiply(BigDecimal.ONE.add(factor));
                boolean touched = position.direction() == TradeDirection.LONG
                        ? candle.low().compareTo(level) <= 0
                        : candle.high().compareTo(level) >= 0;
                if (touched) {
                    BigDecimal fill = position.direction() == TradeDirection.LONG
                            ? candle.open().min(level)
                            : candle.open().max(level);
                    close(exitTime, fill, TradeExitReason.STOP_LOSS);
                    return;
                }
            }
            BigDecimal trailing = config.trailingStopPct();
            if (trailing != null) {
                BigDecimal factor = trailing.divide(hundred, MATH_CONTEXT);
                BigDecimal level = position.direction() == TradeDirection.LONG
                        ? position.highWaterMark().multiply(BigDecimal.ONE.subtract(factor))
                        : position.lowWaterMark().multiply(BigDecimal.ONE.add(factor));
                boolean touched = position.direction() == TradeDirection.LONG
                        ? candle.low().compareTo(level) <= 0
                        : candle.high().compareTo(level) >= 0;
                if (touched) {
                    BigDecimal fill = position.direction() == TradeDirection.LONG
                            ? candle.open().min(level)
                            : candle.open().max(level);
                    close(exitTime, fill, TradeExitReason.TRAILING_STOP);
                    return;
                }
            }
            BigDecimal target = config.takeProfitPct();
            if (target != null) {
                BigDecimal factor = target.divide(hundred, MATH_CONTEXT);
                BigDecimal level = position.direction() == TradeDirection.LONG
                        ? position.entryPrice().multiply(BigDecimal.ONE.add(factor))
                        : position.entryPrice().multiply(BigDecimal.ONE.subtract(factor));
                boolean touched = position.direction() == TradeDirection.LONG
                        ? candle.high().compareTo(level) >= 0
                        : candle.low().compareTo(level) <= 0;
                if (touched) {
                    BigDecimal fill = position.direction() == TradeDirection.LONG
                            ? candle.open().max(level)
                            : candle.open().min(level);
                    close(exitTime, fill, TradeExitReason.TAKE_PROFIT);
                    return;
                }
            }
            position = position.withCompletedCandle(candle);
        }

        private void open(TradeDirection direction, Instant at, BigDecimal price) {
            BigDecimal committedCapital = cash
                    .multiply(config.positionSizePct())
                    .divide(new BigDecimal("100"), MATH_CONTEXT);
            BigDecimal denominator = price.multiply(BigDecimal.ONE.add(config.feeRate()));
            BigDecimal quantity = committedCapital.divide(denominator, MATH_CONTEXT);
            BigDecimal entryValue = quantity.multiply(price);
            BigDecimal entryFee = entryValue.multiply(config.feeRate());
            position = new Position(
                    at, price, quantity, entryFee, committedCapital, direction, price, price);
            cash = cash.subtract(committedCapital);
        }

        private void liquidateAtDatasetEnd(Instant at, BigDecimal price) {
            if (position != null) {
                close(at, price, TradeExitReason.DATASET_END);
            }
        }

        private void close(Instant at, BigDecimal price, TradeExitReason exitReason) {
            BigDecimal exitValue = position.quantity().multiply(price);
            BigDecimal exitFee = exitValue.multiply(config.feeRate());
            BigDecimal totalFee = position.entryFee().add(exitFee);
            BigDecimal priceChange = position.direction() == TradeDirection.LONG
                    ? price.subtract(position.entryPrice())
                    : position.entryPrice().subtract(price);
            BigDecimal pnl = priceChange.multiply(position.quantity())
                    .subtract(totalFee);
            cash = position.direction() == TradeDirection.LONG
                    ? cash.add(exitValue.subtract(exitFee))
                    : cash.add(position.committedCapital()).add(pnl);
            trades.add(new Trade(
                    position.entryTime(),
                    position.entryPrice(),
                    at,
                    price,
                    position.quantity(),
                    totalFee,
                    pnl,
                    position.direction(),
                    exitReason));
            position = null;
        }

        private BigDecimal equityAt(BigDecimal markPrice) {
            if (position == null) {
                return cash;
            }
            BigDecimal exitValue = position.quantity().multiply(markPrice);
            if (position.direction() == TradeDirection.LONG) {
                return cash.add(exitValue.subtract(exitValue.multiply(config.feeRate())));
            }
            BigDecimal priceChange = position.entryPrice().subtract(markPrice);
            BigDecimal estimatedExitFee = position.quantity().multiply(markPrice).multiply(config.feeRate());
            return cash.add(position.committedCapital())
                    .add(priceChange.multiply(position.quantity()))
                    .subtract(position.entryFee())
                    .subtract(estimatedExitFee);
        }

        private BigDecimal cash() {
            return cash;
        }

        private List<Trade> trades() {
            return List.copyOf(trades);
        }
    }

    private record Position(
            Instant entryTime,
            BigDecimal entryPrice,
            BigDecimal quantity,
            BigDecimal entryFee,
            BigDecimal committedCapital,
            TradeDirection direction,
            BigDecimal highWaterMark,
            BigDecimal lowWaterMark) {

        private Position withCompletedCandle(Candle candle) {
            return new Position(
                    entryTime,
                    entryPrice,
                    quantity,
                    entryFee,
                    committedCapital,
                    direction,
                    highWaterMark.max(candle.high()),
                    lowWaterMark.min(candle.low()));
        }
    }
}
