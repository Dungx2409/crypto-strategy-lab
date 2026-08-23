package com.cryptolab.infrastructure.strategy.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.strategy.domain.StrategyDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NewsSentimentStrategyFactoryTest {

    @Test
    void createsTheVersionedPluginWithDefaults() {
        var factory = new NewsSentimentStrategyFactory();
        var strategy = factory.create(new StrategyDefinition("NEWS_SENTIMENT", "1.0", Map.of()));

        assertThat(factory.parameterSchema()).containsKeys(
                "windowMinutes", "buyThreshold", "sellThreshold");
        assertThat(strategy.descriptor().type()).isEqualTo("NEWS_SENTIMENT");
        assertThat(strategy.descriptor().version()).isEqualTo("1.0");
    }
}
