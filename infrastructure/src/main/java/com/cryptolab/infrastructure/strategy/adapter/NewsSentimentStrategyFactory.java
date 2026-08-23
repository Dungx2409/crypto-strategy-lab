package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.extension.NewsSentimentStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class NewsSentimentStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS =
            Set.of("windowMinutes", "buyThreshold", "sellThreshold");

    @Override
    public String type() {
        return NewsSentimentStrategy.TYPE;
    }

    @Override
    public String version() {
        return NewsSentimentStrategy.VERSION;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "windowMinutes", Map.of("type", "integer", "default", 60, "minimum", 1),
                "buyThreshold", Map.of("type", "number", "default", 0.7, "exclusiveMinimum", 0, "maximum", 1),
                "sellThreshold", Map.of("type", "number", "default", -0.7, "minimum", -1, "exclusiveMaximum", 0));
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        return new NewsSentimentStrategy(
                StrategyFactorySupport.integer(parameters, "windowMinutes", 60),
                StrategyFactorySupport.decimal(parameters, "buyThreshold", "0.7"),
                StrategyFactorySupport.decimal(parameters, "sellThreshold", "-0.7"));
    }
}
