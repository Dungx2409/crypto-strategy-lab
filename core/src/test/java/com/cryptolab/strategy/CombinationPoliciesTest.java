package com.cryptolab.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.strategy.domain.CombinedSignal;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import com.cryptolab.strategy.domain.WeightedSignal;
import com.cryptolab.strategy.domain.policy.MajorityVotePolicy;
import com.cryptolab.strategy.domain.policy.WeightedVotePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CombinationPoliciesTest {

    private static final Instant EARLIER = Instant.parse("2026-08-18T01:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-18T01:05:00Z");

    @Test
    void majorityVoteMapsBuyHoldSellAndIgnoresWeights() {
        MajorityVotePolicy policy = new MajorityVotePolicy();

        CombinedSignal buy = policy.combine(List.of(
                weighted("MA", SignalType.BUY, "0.01", EARLIER),
                weighted("RSI", SignalType.BUY, "0", LATER),
                weighted("SR", SignalType.SELL, "100", EARLIER)));
        CombinedSignal hold = policy.combine(List.of(
                weighted("MA", SignalType.BUY, "1", EARLIER),
                weighted("RSI", SignalType.SELL, "1", LATER)));
        CombinedSignal sell = policy.combine(List.of(
                weighted("MA", SignalType.HOLD, "1", EARLIER),
                weighted("RSI", SignalType.SELL, "1", LATER)));

        assertThat(buy.type()).isEqualTo(SignalType.BUY);
        assertThat(buy.score()).isEqualByComparingTo("1");
        assertThat(buy.at()).isEqualTo(LATER);
        assertThat(hold.type()).isEqualTo(SignalType.HOLD);
        assertThat(sell.type()).isEqualTo(SignalType.SELL);
    }

    @Test
    void weightedVoteUsesConfigurableExclusiveThreshold() {
        WeightedVotePolicy policy = new WeightedVotePolicy(new BigDecimal("0.10"));

        CombinedSignal buy = policy.combine(List.of(
                weighted("MA", SignalType.BUY, "0.2", EARLIER),
                weighted("RSI", SignalType.SELL, "0.3", EARLIER),
                weighted("SR", SignalType.BUY, "0.5", LATER)));
        CombinedSignal boundaryHold = policy.combine(List.of(
                weighted("MA", SignalType.BUY, "0.10", EARLIER)));
        CombinedSignal sell = policy.combine(List.of(
                weighted("RSI", SignalType.SELL, "0.11", LATER)));

        assertThat(buy.score()).isEqualByComparingTo("0.4");
        assertThat(buy.type()).isEqualTo(SignalType.BUY);
        assertThat(buy.at()).isEqualTo(LATER);
        assertThat(boundaryHold.type()).isEqualTo(SignalType.HOLD);
        assertThat(sell.type()).isEqualTo(SignalType.SELL);
        assertThat(policy.threshold()).isEqualByComparingTo("0.10");
    }

    @Test
    void policiesRejectAnEmptySignalSetAndInvalidThreshold() {
        assertThatThrownBy(() -> new MajorityVotePolicy().combine(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("at least one signal is required");
        assertThatThrownBy(() -> new WeightedVotePolicy(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("threshold must not be negative");
    }

    private static WeightedSignal weighted(String type, SignalType signalType, String weight, Instant at) {
        BigDecimal strength = BigDecimal.valueOf(switch (signalType) {
            case BUY -> 1;
            case SELL -> -1;
            case HOLD -> 0;
        });
        return new WeightedSignal(
                new StrategyDescriptor(type, "1.0", Map.of()),
                new Signal(signalType, strength, at, "test"),
                new BigDecimal(weight));
    }
}
