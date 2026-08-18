package com.cryptolab.api.strategy;

import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import java.util.Map;

public record StrategyCatalogResponse(
        String type,
        String version,
        Map<String, Object> parameterSchema) {

    static StrategyCatalogResponse from(StrategyPluginDescriptor descriptor) {
        return new StrategyCatalogResponse(
                descriptor.type(), descriptor.version(), descriptor.parameterSchema());
    }
}
