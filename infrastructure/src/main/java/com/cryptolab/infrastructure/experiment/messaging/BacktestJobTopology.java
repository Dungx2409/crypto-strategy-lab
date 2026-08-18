package com.cryptolab.infrastructure.experiment.messaging;

public final class BacktestJobTopology {

    public static final String JOB_EXCHANGE = "crypto.backtest";
    public static final String JOB_QUEUE = "crypto.backtest.jobs";
    public static final String JOB_ROUTING_KEY = "backtest.job";
    public static final String DEAD_LETTER_EXCHANGE = "crypto.backtest.dlx";
    public static final String DEAD_LETTER_QUEUE = "crypto.backtest.jobs.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "backtest.job.dead";
    public static final String OUTBOX_EVENT_TYPE = "BacktestJobDispatchRequested";
    public static final String RETRY_OUTBOX_EVENT_TYPE = "BacktestJobRetryRequested";

    private BacktestJobTopology() {}
}
