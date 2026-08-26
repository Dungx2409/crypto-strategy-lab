package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.extension.RuleBasedStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class RuleBasedStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS = Set.of(
            "buyMetric", "buyPeriod", "buyOperator", "buyThreshold",
            "sellMetric", "sellPeriod", "sellOperator", "sellThreshold");

    @Override public String type() { return RuleBasedStrategy.TYPE; }
    @Override public String version() { return RuleBasedStrategy.VERSION; }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "buyMetric", choice("RSI", "RSI", "PRICE_CHANGE_PCT", "SMA_DISTANCE_PCT", "VOLUME_CHANGE_PCT"),
                "buyPeriod", integer(14),
                "buyOperator", choice("LTE", "LT", "LTE", "GT", "GTE"),
                "buyThreshold", decimal(30),
                "sellMetric", choice("RSI", "RSI", "PRICE_CHANGE_PCT", "SMA_DISTANCE_PCT", "VOLUME_CHANGE_PCT"),
                "sellPeriod", integer(14),
                "sellOperator", choice("GTE", "LT", "LTE", "GT", "GTE"),
                "sellThreshold", decimal(70));
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        return new RuleBasedStrategy(
                metric(parameters, "buyMetric", "RSI"),
                StrategyFactorySupport.integer(parameters, "buyPeriod", 14),
                operator(parameters, "buyOperator", "LTE"),
                StrategyFactorySupport.decimal(parameters, "buyThreshold", "30"),
                metric(parameters, "sellMetric", "RSI"),
                StrategyFactorySupport.integer(parameters, "sellPeriod", 14),
                operator(parameters, "sellOperator", "GTE"),
                StrategyFactorySupport.decimal(parameters, "sellThreshold", "70"));
    }

    private static RuleBasedStrategy.Metric metric(Map<String, Object> values, String name, String fallback) {
        try {
            return RuleBasedStrategy.Metric.valueOf(String.valueOf(values.getOrDefault(name, fallback)).toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(name + " is not a supported generated metric", invalid);
        }
    }

    private static RuleBasedStrategy.Operator operator(Map<String, Object> values, String name, String fallback) {
        try {
            return RuleBasedStrategy.Operator.valueOf(String.valueOf(values.getOrDefault(name, fallback)).toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(name + " is not a supported generated operator", invalid);
        }
    }

    private static Map<String, Object> choice(String defaultValue, String... values) {
        return Map.of("type", "string", "default", defaultValue, "enum", java.util.List.of(values));
    }

    private static Map<String, Object> integer(int defaultValue) {
        return Map.of("type", "integer", "default", defaultValue, "minimum", 2, "maximum", 500);
    }

    private static Map<String, Object> decimal(int defaultValue) {
        return Map.of("type", "number", "default", defaultValue);
    }
}
