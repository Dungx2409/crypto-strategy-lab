package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.baseline.BollingerBandsStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class BollingerBandsStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS = Set.of("window", "deviationMultiplier");

    @Override
    public String type() {
        return BollingerBandsStrategy.TYPE;
    }

    @Override
    public String version() {
        return BollingerBandsStrategy.VERSION;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "window", Map.of("type", "integer", "default", 20, "minimum", 2),
                "deviationMultiplier", Map.of("type", "number", "default", 2, "exclusiveMinimum", 0));
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        return new BollingerBandsStrategy(
                StrategyFactorySupport.integer(parameters, "window", 20),
                StrategyFactorySupport.decimal(parameters, "deviationMultiplier", "2"));
    }
}
