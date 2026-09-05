package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.SortDirection;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboard")
public final class LeaderboardController {

    private final ExperimentPipelineService pipeline;

    public LeaderboardController(ExperimentPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @GetMapping
    public LeaderboardResponse leaderboard(
            @RequestParam UUID searchRunId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return LeaderboardResponse.from(
                searchRunId,
                pipeline.leaderboard(
                        searchRunId,
                        limit,
                        LeaderboardSort.parse(sort),
                        SortDirection.parse(direction)));
    }

    @GetMapping("/all-time")
    public LeaderboardResponse allTime(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return LeaderboardResponse.from(
                null,
                pipeline.allTimeLeaderboard(
                        limit,
                        LeaderboardSort.parse(sort),
                        SortDirection.parse(direction)));
    }
}
