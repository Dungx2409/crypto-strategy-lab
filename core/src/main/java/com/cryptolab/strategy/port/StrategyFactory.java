package com.cryptolab.strategy.port;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.util.Map;

public interface StrategyFactory {

    String type();

    String version();

    Map<String, Object> parameterSchema();

    Strategy create(StrategyDefinition definition);
}
