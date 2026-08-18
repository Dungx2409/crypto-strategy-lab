package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.LeaderboardEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LeaderboardResponse(UUID searchRunId, List<Item> items) {

    static LeaderboardResponse from(UUID searchRunId, List<LeaderboardEntry> entries) {
        return new LeaderboardResponse(
                searchRunId,
                entries.stream().map(Item::from).toList());
    }

    public record Item(
            int rank,
            UUID experimentId,
            String strategySummary,
            BigDecimal returnPct,
            BigDecimal maxDrawdownPct,
            int totalTrades,
            BigDecimal score) {

        private static Item from(LeaderboardEntry entry) {
            var ranking = entry.ranking();
            return new Item(
                    ranking.rank(),
                    ranking.experimentId(),
                    entry.strategySummary(),
                    ranking.metrics().totalReturnPct(),
                    ranking.metrics().maxDrawdownPct(),
                    ranking.metrics().totalTrades(),
                    ranking.metrics().score());
        }
    }
}
