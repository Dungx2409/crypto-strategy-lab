package com.cryptolab.api.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cryptolab.api.experiment.StompLeaderboardUpdatePublisher;
import com.cryptolab.api.search.SearchRunResponse;
import com.cryptolab.api.search.StompSearchProgressPublisher;
import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.SearchStopReason;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class StompRealtimePublisherTest {

    @Test
    void isolatesSearchAndLeaderboardTopicsBySearchRunId() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        UUID searchRunId = UUID.fromString("76000000-0000-0000-0000-000000000001");
        Instant createdAt = Instant.parse("2026-08-18T00:00:00Z");
        SearchContext context = new SearchContext(
                searchRunId,
                new MarketDatasetRef(
                        "BTCUSDT",
                        Timeframe.M5,
                        createdAt,
                        createdAt.plusSeconds(300),
                        "realtime-test-v1",
                        "checksum"),
                List.of("MA"),
                Map.of("MA", "1.0"),
                new SearchParameterSpace(Map.of(
                        "MA", Map.of("fastPeriod", List.of(10), "slowPeriod", List.of(20)))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                11L,
                new StopConditions(100L, null, null),
                20);
        SearchRun run = new SearchRun(
                searchRunId,
                SearchRunStatus.RUNNING,
                context,
                "genetic",
                "1.0",
                createdAt,
                createdAt,
                null,
                false);
        SearchRunSummary summary = new SearchRunSummary(
                run, 3, 3, 0, 0, 1, 2, 0, 0,
                null, 0, SearchStopReason.MAX_CANDIDATES, null, null);
        LeaderboardUpdatedEvent leaderboard =
                new LeaderboardUpdatedEvent(searchRunId, List.of(), Instant.EPOCH);

        new StompSearchProgressPublisher(template).publish(summary);
        new StompLeaderboardUpdatePublisher(template).publish(leaderboard);

        verify(template).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/search/" + searchRunId),
                org.mockito.ArgumentMatchers.any(SearchRunResponse.class));
        verify(template).convertAndSend("/topic/leaderboard/" + searchRunId, leaderboard);
    }
}
