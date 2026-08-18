package com.cryptolab.strategy.domain.policy;

import com.cryptolab.strategy.domain.CombinationPolicy;
import com.cryptolab.strategy.domain.CombinedSignal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.WeightedSignal;
import java.math.BigDecimal;
import java.util.List;

public final class MajorityVotePolicy implements CombinationPolicy {

    @Override
    public CombinedSignal combine(List<WeightedSignal> signals) {
        List<WeightedSignal> validated = CombinationPolicySupport.requireSignals(signals);
        int score = validated.stream()
                .mapToInt(weighted -> CombinationPolicySupport.direction(weighted.signal().type()))
                .sum();
        SignalType type = score > 0 ? SignalType.BUY : score < 0 ? SignalType.SELL : SignalType.HOLD;
        return new CombinedSignal(
                type,
                BigDecimal.valueOf(score),
                CombinationPolicySupport.latestTimestamp(validated));
    }
}
