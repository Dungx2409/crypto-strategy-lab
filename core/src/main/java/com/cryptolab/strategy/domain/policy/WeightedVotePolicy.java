package com.cryptolab.strategy.domain.policy;

import com.cryptolab.strategy.domain.CombinationPolicy;
import com.cryptolab.strategy.domain.CombinedSignal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.WeightedSignal;
import java.math.BigDecimal;
import java.util.List;

public final class WeightedVotePolicy implements CombinationPolicy {

    public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.10");

    private final BigDecimal threshold;

    public WeightedVotePolicy() {
        this(DEFAULT_THRESHOLD);
    }

    public WeightedVotePolicy(BigDecimal threshold) {
        if (threshold == null || threshold.signum() < 0) {
            throw new IllegalArgumentException("threshold must not be negative");
        }
        this.threshold = threshold;
    }

    public BigDecimal threshold() {
        return threshold;
    }

    @Override
    public CombinedSignal combine(List<WeightedSignal> signals) {
        List<WeightedSignal> validated = CombinationPolicySupport.requireSignals(signals);
        BigDecimal score = validated.stream()
                .map(weighted -> weighted.weight().multiply(BigDecimal.valueOf(
                        CombinationPolicySupport.direction(weighted.signal().type()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        SignalType type = score.compareTo(threshold) > 0
                ? SignalType.BUY
                : score.compareTo(threshold.negate()) < 0 ? SignalType.SELL : SignalType.HOLD;
        return new CombinedSignal(type, score, CombinationPolicySupport.latestTimestamp(validated));
    }
}
