package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyOverlayDescriptor;
import java.util.List;
import com.cryptolab.strategy.domain.baseline.SupportResistanceStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SupportResistanceStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS = Set.of("window");

    @Override
    public String type() {
        return SupportResistanceStrategy.TYPE;
    }

    @Override
    public String version() {
        return SupportResistanceStrategy.VERSION;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of("window", Map.of("type", "integer", "default", 20, "minimum", 2));
    }

    @Override
    public List<StrategyOverlayDescriptor> overlays() {
        return List.of(new StrategyOverlayDescriptor(
                "support-resistance",
                "PRICE_CHANNEL",
                Map.of("periodParameter", "window", "color", "#0891b2")));
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        return new SupportResistanceStrategy(
                StrategyFactorySupport.integer(parameters, "window", 20));
    }
}
