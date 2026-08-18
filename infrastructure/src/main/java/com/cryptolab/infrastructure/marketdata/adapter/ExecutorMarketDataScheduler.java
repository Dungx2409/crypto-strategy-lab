package com.cryptolab.infrastructure.marketdata.adapter;

import com.cryptolab.marketdata.port.MarketDataScheduler;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorMarketDataScheduler implements MarketDataScheduler {

    private final ScheduledExecutorService executor;

    public ExecutorMarketDataScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public ScheduledTask schedule(Duration delay, Runnable task) {
        var future = executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }
}
