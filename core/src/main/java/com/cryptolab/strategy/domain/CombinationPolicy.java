package com.cryptolab.strategy.domain;

import java.util.List;

public interface CombinationPolicy {
    CombinedSignal combine(List<WeightedSignal> signals);
}
