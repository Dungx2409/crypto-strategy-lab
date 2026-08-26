package com.cryptolab.api.experiment;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.application.ExperimentPlanFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments")
public final class ExperimentController {

    private final ExperimentPlanFactory plans;
    private final ExperimentPipelineService pipeline;

    public ExperimentController(ExperimentPlanFactory plans, ExperimentPipelineService pipeline) {
        this.plans = plans;
        this.pipeline = pipeline;
    }

    @PostMapping
    public ResponseEntity<ExperimentDetailsResponse> runSingleCandidate(
            @RequestBody SingleExperimentRequest request,
            HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        ExperimentDetailsResponse response = ExperimentDetailsResponse.from(
                pipeline.execute(plans.create(request.toCommand(account.id()))));
        return ResponseEntity.created(URI.create("/api/v1/experiments/" + response.experimentId()))
                .body(response);
    }

    @GetMapping("/{experimentId}")
    public ExperimentDetailsResponse details(
            @PathVariable UUID experimentId, HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        return ExperimentDetailsResponse.from(pipeline.details(account.id(), experimentId));
    }

    @GetMapping("/{experimentId}/provenance")
    public ExperimentProvenanceResponse provenance(
            @PathVariable UUID experimentId, HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        return ExperimentProvenanceResponse.from(pipeline.provenance(account.id(), experimentId));
    }

    @GetMapping("/{experimentId}/dataset")
    public ExperimentDatasetResponse dataset(
            @PathVariable UUID experimentId, HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        return ExperimentDatasetResponse.from(pipeline.dataset(account.id(), experimentId));
    }

    @PostMapping("/{experimentId}/rerun")
    public RerunResponse rerun(
            @PathVariable UUID experimentId, HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        return RerunResponse.from(pipeline.rerun(account.id(), experimentId));
    }
}
