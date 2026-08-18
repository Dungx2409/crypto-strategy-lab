package com.cryptolab.experiment.port;

import com.cryptolab.strategy.domain.CombinationPolicy;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;

public interface CombinationPolicyResolver {
    CombinationPolicy resolve(CombinationPolicyDefinition definition);
}
