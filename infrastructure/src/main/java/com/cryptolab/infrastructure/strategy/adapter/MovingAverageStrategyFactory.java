package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyOverlayDescriptor;
import java.util.List;
import com.cryptolab.strategy.domain.baseline.MovingAverageStrategy;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class MovingAverageStrategyFactory implements StrategyFactory {

    private static final Set<String> PARAMETERS = Set.of("fastPeriod", "slowPeriod");

    @Override
    public String type() {
        return MovingAverageStrategy.TYPE;
    }

    @Override
    public String version() {
        return MovingAverageStrategy.VERSION;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "fastPeriod", Map.of("type", "integer", "default", 10, "minimum", 1),
                "slowPeriod", Map.of("type", "integer", "default", 20, "minimum", 2));
    }

    @Override
    public List<StrategyOverlayDescriptor> overlays() {
        return List.of(
                new StrategyOverlayDescriptor(
                        "fast-average", "SMA", Map.of("periodParameter", "fastPeriod", "color", "#2563eb")),
                new StrategyOverlayDescriptor(
                        "slow-average", "SMA", Map.of("periodParameter", "slowPeriod", "color", "#f59e0b")));
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        Map<String, Object> parameters = StrategyFactorySupport.validate(
                definition, type(), version(), PARAMETERS);
        return new MovingAverageStrategy(
                StrategyFactorySupport.integer(parameters, "fastPeriod", 10),
                StrategyFactorySupport.integer(parameters, "slowPeriod", 20));
    }
}
