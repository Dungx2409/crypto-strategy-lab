package com.cryptolab.marketdata.port;

import java.time.Duration;

@FunctionalInterface
public interface MarketDataScheduler {

    ScheduledTask schedule(Duration delay, Runnable task);

    @FunctionalInterface
    interface ScheduledTask {
        void cancel();
    }
}
