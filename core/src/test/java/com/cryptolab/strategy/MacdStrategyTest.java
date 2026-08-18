package com.cryptolab.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.extension.MacdStrategy;
import org.junit.jupiter.api.Test;

class MacdStrategyTest {

    @Test
    void signalsOnlyWhenMacdCrossesItsSignalLine() {
        MacdStrategy strategy = new MacdStrategy(2, 3, 2);

        assertThat(strategy.analyze(StrategyTestFixtures.context("5", "4", "3", "2", "1", "10")).type())
                .isEqualTo(SignalType.BUY);
        assertThat(strategy.analyze(StrategyTestFixtures.context("1", "2", "3", "4", "5", "0")).type())
                .isEqualTo(SignalType.SELL);
        assertThat(strategy.analyze(StrategyTestFixtures.context("1", "2", "3", "4", "5", "6")).type())
                .isEqualTo(SignalType.HOLD);
    }

    @Test
    void exposesImmutableReproducibleDescriptorAndDefaultsToHoldDuringWarmup() {
        MacdStrategy strategy = new MacdStrategy(12, 26, 9);

        assertThat(strategy.descriptor().type()).isEqualTo("MACD");
        assertThat(strategy.descriptor().version()).isEqualTo("1.0");
        assertThat(strategy.descriptor().parameters())
                .containsEntry("fastPeriod", 12)
                .containsEntry("slowPeriod", 26)
                .containsEntry("signalPeriod", 9);
        assertThat(strategy.analyze(StrategyTestFixtures.context("100")).type())
                .isEqualTo(SignalType.HOLD);
    }

    @Test
    void rejectsInvalidPeriods() {
        assertThatThrownBy(() -> new MacdStrategy(0, 26, 9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MacdStrategy(26, 12, 9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MacdStrategy(12, 26, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
