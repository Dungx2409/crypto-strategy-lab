package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.baseline.RsiStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class RsiStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS = Set.of("period", "oversold", "overbought");

    @Override
    public String type() {
        return RsiStrategy.TYPE;
    }

    @Override
    public String version() {
        return RsiStrategy.VERSION;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "period", Map.of("type", "integer", "default", 14, "minimum", 2),
                "oversold", Map.of("type", "number", "default", 30, "minimum", 0, "maximum", 100),
                "overbought", Map.of("type", "number", "default", 70, "minimum", 0, "maximum", 100));
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        return new RsiStrategy(
                StrategyFactorySupport.integer(parameters, "period", 14),
                StrategyFactorySupport.decimal(parameters, "oversold", "30"),
                StrategyFactorySupport.decimal(parameters, "overbought", "70"));
    }
}
