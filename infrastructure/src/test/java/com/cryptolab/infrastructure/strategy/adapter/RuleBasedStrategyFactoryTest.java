package com.cryptolab.infrastructure.strategy.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.extension.GeneratedRuleJavaSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBasedStrategyFactoryTest {

    private final RuleBasedStrategyFactory factory = new RuleBasedStrategyFactory();

    @Test
    void createsValidatedRuntimeRuleWithoutGeneratedSourceCode() {
        var strategy = factory.create(new StrategyDefinition("RULE", "1.0", Map.of(
                "buyMetric", "PRICE_CHANGE_PCT",
                "buyPeriod", 2,
                "buyOperator", "GT",
                "buyThreshold", 1,
                "sellMetric", "RSI",
                "sellPeriod", 14,
                "sellOperator", "GTE",
                "sellThreshold", 70)));

        assertThat(strategy.descriptor().type()).isEqualTo("RULE");
        assertThat(strategy.descriptor().parameters()).containsEntry("buyMetric", "PRICE_CHANGE_PCT");
    }

    @Test
    void rejectsMetricsOutsideTheDslAllowlist() {
        assertThatThrownBy(() -> factory.create(new StrategyDefinition(
                "RULE", "1.0", Map.of("buyMetric", "EXECUTE_JAVA"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buyMetric");
    }

    @Test
    void compilesAndLoadsTheDeterministicJavaGeneratedFromAValidatedRule() {
        StrategyDefinition definition = new StrategyDefinition("RULE", "1.0", Map.of(
                "buyMetric", "PRICE_CHANGE_PCT", "buyPeriod", 2,
                "buyOperator", "GT", "buyThreshold", 1,
                "sellMetric", "RSI", "sellPeriod", 14,
                "sellOperator", "GTE", "sellThreshold", 70));
        StrategyDefinition generated = GeneratedRuleJavaSource.enrich(definition);

        var strategy = factory.create(generated);

        assertThat(strategy.descriptor().parameters())
                .containsKeys("generatedClassName", "generatedJavaSource");
    }

    @Test
    void rejectsEditedGeneratedJava() {
        StrategyDefinition generated = GeneratedRuleJavaSource.enrich(
                new StrategyDefinition("RULE", "1.0", Map.of()));
        var changed = new java.util.HashMap<>(generated.parameters());
        changed.put("generatedJavaSource", "public class Injected {}");

        assertThatThrownBy(() -> factory.create(
                new StrategyDefinition("RULE", "1.0", changed)))
                .hasMessageContaining("does not match");
    }
}
