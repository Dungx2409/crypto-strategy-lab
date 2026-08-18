package com.cryptolab.strategy.port;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import java.util.List;
import java.util.Set;

public interface StrategyRegistry {

    void register(StrategyFactory factory);

    Strategy create(StrategyDefinition definition);

    Set<String> registeredTypes();

    List<StrategyPluginDescriptor> availableStrategies();
}
