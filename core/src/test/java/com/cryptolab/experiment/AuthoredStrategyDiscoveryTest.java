package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.RandomStrategyGenerator;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.port.StrategyFactory;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AuthoredStrategyDiscoveryTest {

    @Test
    void discoveryCanUseAuthoredStrategyHiddenFromSharedCatalog() {
        AuthoredRegistry registry = new AuthoredRegistry();
        SearchContext context = new SearchContext(
                ExperimentTestFixtures.EXPERIMENT_ID,
                ExperimentTestFixtures.dataset().reference(),
                List.of("AI_DSL"),
                Map.of("AI_DSL", "1.0"),
                new SearchParameterSpace(Map.of(
                        "AI_DSL",
                        Map.of("source", List.of("BUY WHEN CLOSE > SMA(CLOSE, 10)\nSELL WHEN CLOSE < SMA(CLOSE, 10)")))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                7L,
                new StopConditions(1L, null, null),
                1);

        var candidate = new RandomStrategyGenerator(registry).generate(context).findFirst();

        assertThat(candidate).isPresent();
        assertThat(candidate.orElseThrow().strategies())
                .singleElement()
                .satisfies(strategy -> {
                    assertThat(strategy.type()).isEqualTo("AI_DSL");
                    assertThat(strategy.parameters()).containsKey("source");
                });
        assertThat(registry.created.get()).isNotNull();
    }

    private static final class AuthoredRegistry implements StrategyRegistry {

        private final AtomicReference<StrategyDefinition> created = new AtomicReference<>();

        @Override
        public void register(StrategyFactory factory) {}

        @Override
        public Strategy create(StrategyDefinition definition) {
            created.set(definition);
            return null;
        }

        @Override
        public Set<String> registeredTypes() {
            return Set.of("MA", "AI_DSL");
        }

        @Override
        public List<StrategyPluginDescriptor> availableStrategies() {
            return List.of(new StrategyPluginDescriptor(
                    "MA",
                    "1.0",
                    Map.of("fastPeriod", Map.of("default", 10))));
        }

        @Override
        public List<StrategyPluginDescriptor> authoringStrategies() {
            return List.of(new StrategyPluginDescriptor(
                    "AI_DSL",
                    "1.0",
                    Map.of("source", Map.of("type", "string"))));
        }
    }
}
