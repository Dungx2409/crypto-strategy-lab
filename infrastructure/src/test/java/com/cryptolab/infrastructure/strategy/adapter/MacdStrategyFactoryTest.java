package com.cryptolab.infrastructure.strategy.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MacdStrategyFactoryTest {

    private final MacdStrategyFactory factory = new MacdStrategyFactory();

    @Test
    void exposesSchemaAndCreatesMacdFromDefaults() {
        Strategy strategy = factory.create(new StrategyDefinition("MACD", "1.0", Map.of()));

        assertThat(factory.parameterSchema()).containsKeys("fastPeriod", "slowPeriod", "signalPeriod");
        assertThat(strategy.descriptor().parameters())
                .containsEntry("fastPeriod", 12)
                .containsEntry("slowPeriod", 26)
                .containsEntry("signalPeriod", 9);
    }

    @Test
    void validatesUnknownParametersAndPeriodRelationships() {
        assertThatThrownBy(() -> factory.create(new StrategyDefinition(
                        "MACD", "1.0", Map.of("databaseColumn", true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown MACD parameter");
        assertThatThrownBy(() -> factory.create(new StrategyDefinition(
                        "MACD", "1.0", Map.of("fastPeriod", 30, "slowPeriod", 20))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slowPeriod");
    }
}
