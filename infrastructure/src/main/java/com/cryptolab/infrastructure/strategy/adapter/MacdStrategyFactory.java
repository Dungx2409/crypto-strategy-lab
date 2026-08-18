package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.extension.MacdStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class MacdStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS = Set.of("fastPeriod", "slowPeriod", "signalPeriod");

    @Override
    public String type() {
        return MacdStrategy.TYPE;
    }

    @Override
    public String version() {
        return MacdStrategy.VERSION;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "fastPeriod", Map.of("type", "integer", "default", 12, "minimum", 1),
                "slowPeriod", Map.of("type", "integer", "default", 26, "minimum", 2),
                "signalPeriod", Map.of("type", "integer", "default", 9, "minimum", 1));
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        return new MacdStrategy(
                StrategyFactorySupport.integer(parameters, "fastPeriod", 12),
                StrategyFactorySupport.integer(parameters, "slowPeriod", 26),
                StrategyFactorySupport.integer(parameters, "signalPeriod", 9));
    }
}
