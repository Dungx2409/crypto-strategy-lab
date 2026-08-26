package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.marketdata.domain.Timeframe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/leaderboard")
public final class PublicLeaderboardController {

    private final ExperimentPipelineService pipeline;

    public PublicLeaderboardController(ExperimentPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @GetMapping
    public LeaderboardResponse leaderboard(
            @RequestParam String symbol,
            @RequestParam String timeframe,
            @RequestParam(defaultValue = "50") int limit) {
        var entries = pipeline.publicDiscoveryLeaderboard(
                symbol, Timeframe.fromExchangeCode(timeframe), limit);
        return LeaderboardResponse.from(null, entries);
    }
}
