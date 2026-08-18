package com.cryptolab.infrastructure.experiment.messaging;

public final class DomainEventTopology {

    public static final String EXCHANGE = "crypto.domain.events.exchange";
    public static final String EVALUATION_QUEUE = "crypto.domain.events.evaluation";
    public static final String RANKING_QUEUE = "crypto.domain.events.ranking";
    public static final String SEARCH_PROGRESS_QUEUE = "crypto.domain.events.search-progress";
    public static final String LEADERBOARD_QUEUE = "crypto.domain.events.leaderboard";
    public static final String DEAD_LETTER_EXCHANGE = "crypto.domain.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "crypto.domain.events.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "domain.event.dead";
    public static final String BACKTEST_COMPLETED_ROUTING_KEY = "domain.event.BacktestCompleted";
    public static final String BACKTEST_COMPLETED_EVENT_TYPE = "BacktestCompleted";
    public static final String STRATEGY_EVALUATED_ROUTING_KEY = "domain.event.StrategyEvaluated";
    public static final String STRATEGY_EVALUATED_EVENT_TYPE = "StrategyEvaluated";
    public static final String LEADERBOARD_UPDATED_ROUTING_KEY = "domain.event.LeaderboardUpdated";
    public static final String LEADERBOARD_UPDATED_EVENT_TYPE = "LeaderboardUpdated";

    private DomainEventTopology() {}
}
