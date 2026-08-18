package com.cryptolab.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.baseline.BollingerBandsStrategy;
import com.cryptolab.strategy.domain.baseline.MovingAverageStrategy;
import com.cryptolab.strategy.domain.baseline.RsiStrategy;
import com.cryptolab.strategy.domain.baseline.SupportResistanceStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineStrategiesTest {

    @Test
    void movingAverageSignalsOnlyOnCrossovers() {
        MovingAverageStrategy strategy = new MovingAverageStrategy(2, 3);

        assertSignal(strategy.analyze(StrategyTestFixtures.context("3", "2", "1", "4")), SignalType.BUY, "1");
        assertSignal(strategy.analyze(StrategyTestFixtures.context("1", "2", "3", "0")), SignalType.SELL, "-1");
        assertSignal(strategy.analyze(StrategyTestFixtures.context("1", "2", "3", "4")), SignalType.HOLD, "0");
    }

    @Test
    void rsiUsesConfiguredThresholds() {
        RsiStrategy strategy = new RsiStrategy(3, new BigDecimal("30"), new BigDecimal("70"));

        assertSignal(strategy.analyze(StrategyTestFixtures.context("100", "90", "80", "70")), SignalType.BUY, "1");
        assertSignal(strategy.analyze(StrategyTestFixtures.context("70", "80", "90", "100")), SignalType.SELL, "-1");
        assertSignal(strategy.analyze(StrategyTestFixtures.context("100", "110", "100", "110")), SignalType.HOLD, "0");
    }

    @Test
    void bollingerBandsCompareCloseAgainstConfiguredBands() {
        BollingerBandsStrategy strategy = new BollingerBandsStrategy(5, new BigDecimal("1.5"));

        assertSignal(strategy.analyze(StrategyTestFixtures.context("100", "100", "100", "100", "50")), SignalType.BUY, "1");
        assertSignal(strategy.analyze(StrategyTestFixtures.context("100", "100", "100", "100", "150")), SignalType.SELL, "-1");
        assertSignal(strategy.analyze(StrategyTestFixtures.context("100", "101", "99", "100", "100")), SignalType.HOLD, "0");
    }

    @Test
    void supportResistanceUsesOnlyThePreviousRollingWindow() {
        SupportResistanceStrategy strategy = new SupportResistanceStrategy(3);
        List<Candle> supportCandles = List.of(
                StrategyTestFixtures.candle(0, decimal("100"), decimal("110"), decimal("90")),
                StrategyTestFixtures.candle(1, decimal("101"), decimal("109"), decimal("91")),
                StrategyTestFixtures.candle(2, decimal("102"), decimal("108"), decimal("92")),
                StrategyTestFixtures.candle(3, decimal("90"), decimal("95"), decimal("89")));
        List<Candle> resistanceCandles = List.of(
                StrategyTestFixtures.candle(0, decimal("100"), decimal("110"), decimal("90")),
                StrategyTestFixtures.candle(1, decimal("101"), decimal("109"), decimal("91")),
                StrategyTestFixtures.candle(2, decimal("102"), decimal("108"), decimal("92")),
                StrategyTestFixtures.candle(3, decimal("110"), decimal("111"), decimal("105")));

        assertSignal(strategy.analyze(context(supportCandles)), SignalType.BUY, "1");
        assertSignal(strategy.analyze(context(resistanceCandles)), SignalType.SELL, "-1");
        assertSignal(strategy.analyze(StrategyTestFixtures.context("100", "101", "102", "103")), SignalType.HOLD, "0");
    }

    @Test
    void insufficientHistoryDeterministicallyReturnsHold() {
        StrategyContext oneCandle = StrategyTestFixtures.context("100");

        assertThat(new MovingAverageStrategy(10, 20).analyze(oneCandle).type()).isEqualTo(SignalType.HOLD);
        assertThat(new RsiStrategy(14, decimal("30"), decimal("70")).analyze(oneCandle).type())
                .isEqualTo(SignalType.HOLD);
        assertThat(new BollingerBandsStrategy(20, decimal("2")).analyze(oneCandle).type())
                .isEqualTo(SignalType.HOLD);
        assertThat(new SupportResistanceStrategy(20).analyze(oneCandle).type()).isEqualTo(SignalType.HOLD);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new MovingAverageStrategy(20, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RsiStrategy(14, decimal("70"), decimal("30")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BollingerBandsStrategy(20, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SupportResistanceStrategy(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void strategyContextRejectsMixedOrUnorderedMarketData() {
        Candle valid = StrategyTestFixtures.candle(0, decimal("100"), decimal("110"), decimal("90"));
        Candle wrongPair = new Candle(
                "ETHUSDT",
                Timeframe.M5,
                valid.openTime().plusSeconds(300),
                decimal("100"), decimal("110"), decimal("90"), decimal("100"), BigDecimal.ONE);

        assertThatThrownBy(() -> new StrategyContext(
                        new TradingPair("BTCUSDT"),
                        Timeframe.M5,
                        List.of(valid, wrongPair),
                        StrategyTestFixtures.START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trading pair");
        assertThatThrownBy(() -> new StrategyContext(
                        new TradingPair("BTCUSDT"),
                        Timeframe.M5,
                        List.of(valid, valid),
                        StrategyTestFixtures.START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly ordered");
    }

    private static StrategyContext context(List<Candle> candles) {
        return new StrategyContext(
                new TradingPair("BTCUSDT"), Timeframe.M5, candles, StrategyTestFixtures.START.plusSeconds(1_200));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertSignal(Signal signal, SignalType type, String strength) {
        assertThat(signal.type()).isEqualTo(type);
        assertThat(signal.strength()).isEqualByComparingTo(strength);
        assertThat(signal.reason()).isNotBlank();
    }
}
