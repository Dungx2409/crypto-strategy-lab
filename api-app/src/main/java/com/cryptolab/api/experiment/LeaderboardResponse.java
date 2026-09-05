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
            UUID searchRunId,
            UUID experimentId,
            String strategySummary,
            BigDecimal returnPct,
            BigDecimal maxDrawdownPct,
            int totalTrades,
            BigDecimal winRatePct,
            BigDecimal score) {

        private static Item from(LeaderboardEntry entry) {
            var ranking = entry.ranking();
            return new Item(
                    ranking.rank(),
                    entry.searchRunId(),
                    ranking.experimentId(),
                    entry.strategySummary(),
                    ranking.metrics().totalReturnPct(),
                    ranking.metrics().maxDrawdownPct(),
                    ranking.metrics().totalTrades(),
                    ranking.metrics().winRatePct(),
                    ranking.metrics().score());
        }
    }
}
