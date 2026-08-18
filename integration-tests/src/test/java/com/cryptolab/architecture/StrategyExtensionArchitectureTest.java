package com.cryptolab.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.infrastructure.strategy.adapter.MacdStrategyFactory;
import com.cryptolab.infrastructure.strategy.adapter.SpringStrategyRegistry;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategyExtensionArchitectureTest {

    @Test
    void productionMacdPluginRegistersWithoutChangingConsumersOrPersistence() {
        SpringStrategyRegistry registry = new SpringStrategyRegistry(List.of());

        registry.register(new MacdStrategyFactory());
        Strategy strategy = registry.create(new StrategyDefinition(
                "MACD", "1.0", Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9)));

        assertThat(registry.registeredTypes()).containsExactly("MACD");
        assertThat(registry.availableStrategies())
                .singleElement()
                .satisfies(plugin -> {
                    assertThat(plugin.type()).isEqualTo("MACD");
                    assertThat(plugin.parameterSchema()).containsKeys("fastPeriod", "slowPeriod", "signalPeriod");
                });
        assertThat(strategy.descriptor().parameters())
                .containsEntry("fastPeriod", 12)
                .containsEntry("slowPeriod", 26)
                .containsEntry("signalPeriod", 9);
    }
}
