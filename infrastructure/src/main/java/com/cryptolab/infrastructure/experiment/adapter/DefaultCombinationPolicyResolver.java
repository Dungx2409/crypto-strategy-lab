package com.cryptolab.infrastructure.experiment.adapter;

import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.strategy.domain.CombinationPolicy;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.policy.MajorityVotePolicy;
import com.cryptolab.strategy.domain.policy.WeightedVotePolicy;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public final class DefaultCombinationPolicyResolver implements CombinationPolicyResolver {

    @Override
    public CombinationPolicy resolve(CombinationPolicyDefinition definition) {
        if (!"1.0".equals(definition.version())) {
            throw new IllegalArgumentException(
                    "unsupported combination policy version: " + definition.version());
        }
        return switch (definition.type().trim().toUpperCase(Locale.ROOT)) {
            case "MAJORITY", "MAJORITY_VOTE" -> new MajorityVotePolicy();
            case "WEIGHTED", "WEIGHTED_VOTE" -> definition.threshold() == null
                    ? new WeightedVotePolicy()
                    : new WeightedVotePolicy(definition.threshold());
            default -> throw new IllegalArgumentException(
                    "unsupported combination policy: " + definition.type());
        };
    }
}
