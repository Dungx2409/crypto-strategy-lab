package com.cryptolab.infrastructure.strategy.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.strategy.domain.StrategyDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiDslStrategyFactoryTest {

    @Test
    void registersForAuthoringAndExecutionButNotGlobalDiscovery() {
        AiDslStrategyFactory factory = new AiDslStrategyFactory();
        SpringStrategyRegistry registry = new SpringStrategyRegistry(List.of(factory));

        assertThat(registry.availableStrategies()).isEmpty();
        assertThat(registry.authoringStrategies())
                .extracting(item -> item.type())
                .containsExactly("AI_DSL");
        assertThat(registry.create(new StrategyDefinition(
                "AI_DSL", "1.0", Map.of("source", "BUY WHEN CLOSE > 0 SELL WHEN CLOSE < 0"))))
                .isNotNull();
    }

    @Test
    void requiresStringSource() {
        AiDslStrategyFactory factory = new AiDslStrategyFactory();

        assertThatThrownBy(() -> factory.create(
                new StrategyDefinition("AI_DSL", "1.0", Map.of("source", 42))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source must be a string");
    }
}
