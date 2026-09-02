package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.extension.AiDslStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class AiDslStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS = Set.of("source");

    @Override
    public String type() {
        return AiDslStrategy.TYPE;
    }

    @Override
    public String version() {
        return AiDslStrategy.VERSION;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of("source", Map.of(
                "type", "string",
                "maxLength", AiDslStrategy.MAX_SOURCE_LENGTH,
                "language", "crypto-trading-dsl-v1"));
    }

    @Override
    public boolean availableForDiscovery() {
        return false;
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        Object source = parameters.get("source");
        if (!(source instanceof String text)) {
            throw new IllegalArgumentException("AI_DSL source must be a string");
        }
        return new AiDslStrategy(text);
    }
}
