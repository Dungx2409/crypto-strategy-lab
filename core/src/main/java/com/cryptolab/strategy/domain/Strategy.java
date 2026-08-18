package com.cryptolab.strategy.domain;

public interface Strategy {

    StrategyDescriptor descriptor();

    Signal analyze(StrategyContext context);
}
