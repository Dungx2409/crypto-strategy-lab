package com.cryptolab.api.strategy;

import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.domain.StrategyOverlayDescriptor;
import java.util.List;
import java.util.Map;

public record StrategyCatalogResponse(
        String type,
        String version,
        Map<String, Object> parameterSchema,
        List<StrategyOverlayDescriptor> overlays) {

    static StrategyCatalogResponse from(StrategyPluginDescriptor descriptor) {
        return new StrategyCatalogResponse(
                descriptor.type(),
                descriptor.version(),
                descriptor.parameterSchema(),
                descriptor.overlays());
    }
}
