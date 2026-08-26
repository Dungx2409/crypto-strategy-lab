package com.cryptolab.strategy.port;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyOverlayDescriptor;
import java.util.List;
import java.util.Map;

public interface StrategyFactory {

    String type();

    String version();

    Map<String, Object> parameterSchema();

    default List<StrategyOverlayDescriptor> overlays() {
        return List.of();
    }

    Strategy create(StrategyDefinition definition);
}
