package com.cryptolab.infrastructure.strategy.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.port.StrategyFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaselineStrategyFactoriesTest {

    @Test
    void exposesExactlyTheFourBaselinePluginsWithSchemaDefaults() {
        SpringStrategyRegistry registry = registry();

        assertThat(registry.registeredTypes()).containsExactlyInAnyOrder("MA", "RSI", "BB", "SR");
        assertThat(registry.availableStrategies())
                .extracting(descriptor -> descriptor.type() + "@" + descriptor.version())
                .containsExactly("BB@1.0", "MA@1.0", "RSI@1.0", "SR@1.0");
        assertThat(registry.availableStrategies().get(1).parameterSchema())
                .containsKeys("fastPeriod", "slowPeriod");
    }

    @Test
    void factoriesCreateStrategiesFromDefaultsAndFlexibleNumericValues() {
        SpringStrategyRegistry registry = registry();

        Strategy movingAverage = registry.create(new StrategyDefinition("ma", "1.0", Map.of()));
        Strategy rsi = registry.create(new StrategyDefinition(
                "RSI",
                "1.0",
                Map.of("period", "14", "oversold", new BigDecimal("20"), "overbought", 80)));

        assertThat(movingAverage.descriptor().parameters())
                .containsEntry("fastPeriod", 10)
                .containsEntry("slowPeriod", 20);
        assertThat(rsi.descriptor().parameters())
                .containsEntry("period", 14)
                .containsEntry("oversold", new BigDecimal("20"))
                .containsEntry("overbought", new BigDecimal("80"));
    }

    @Test
    void registryCentralizesUnknownVersionParameterAndDuplicateValidation() {
        SpringStrategyRegistry registry = registry();

        assertThatThrownBy(() -> registry.create(new StrategyDefinition("MA", "2.0", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not registered");
        assertThatThrownBy(() -> registry.create(new StrategyDefinition(
                        "MA", "1.0", Map.of("databaseColumn", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown MA parameter: databaseColumn");
        assertThatThrownBy(() -> registry.register(new MovingAverageStrategyFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("strategy factory already registered: MA@1.0");
    }

    private static SpringStrategyRegistry registry() {
        List<StrategyFactory> factories = List.of(
                new MovingAverageStrategyFactory(),
                new RsiStrategyFactory(),
                new BollingerBandsStrategyFactory(),
                new SupportResistanceStrategyFactory());
        return new SpringStrategyRegistry(factories);
    }
}
