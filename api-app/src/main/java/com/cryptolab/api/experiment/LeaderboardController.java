package com.cryptolab.api.experiment;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.ExperimentPipelineService;
import jakarta.servlet.http.HttpServletRequest;
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
            HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        return LeaderboardResponse.from(
                searchRunId, pipeline.leaderboard(account.id(), searchRunId, limit));
    }
}
