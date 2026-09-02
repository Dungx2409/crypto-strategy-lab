package com.cryptolab.experiment.domain;

import java.util.Locale;

public enum LeaderboardSort {
    RANK,
    SCORE,
    RETURN,
    WIN_RATE,
    MAX_DRAWDOWN,
    TRADES;

    public static LeaderboardSort parse(String value) {
        if (value == null || value.isBlank()) {
            return SCORE;
        }
        return switch (value.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "RANK" -> RANK;
            case "SCORE" -> SCORE;
            case "RETURN", "RETURN_PCT", "TOTAL_RETURN", "TOTAL_RETURN_PCT" -> RETURN;
            case "WIN_RATE", "WIN_RATE_PCT" -> WIN_RATE;
            case "MAX_DRAWDOWN", "MAX_DRAWDOWN_PCT", "DRAWDOWN", "MDD" -> MAX_DRAWDOWN;
            case "TRADES", "TOTAL_TRADES" -> TRADES;
            default -> throw new IllegalArgumentException("unsupported leaderboard sort: " + value);
        };
    }
}
