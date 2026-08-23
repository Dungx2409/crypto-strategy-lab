package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.application.ExperimentPlanFactory;
import com.cryptolab.experiment.application.ExperimentNotFoundException;
import com.cryptolab.experiment.port.ExperimentOwnershipRepository;
import com.cryptolab.api.account.AuthenticatedAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments")
public class ExperimentController {

    private final ExperimentPlanFactory plans;
    private final ExperimentPipelineService pipeline;
    private final ExperimentOwnershipRepository ownership;
    private final Clock clock;

    public ExperimentController(ExperimentPlanFactory plans, ExperimentPipelineService pipeline) {
        this(plans, pipeline, ExperimentOwnershipRepository.none(), Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExperimentController(
            ExperimentPlanFactory plans,
            ExperimentPipelineService pipeline,
            ExperimentOwnershipRepository ownership,
            Clock marketDataClock) {
        this.plans = plans;
        this.pipeline = pipeline;
        this.ownership = ownership;
        this.clock = marketDataClock;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ExperimentDetailsResponse> runSingleCandidate(
            @RequestBody SingleExperimentRequest request, HttpServletRequest servletRequest) {
        AuthenticatedAccount account = AuthenticatedAccount.require(servletRequest.getSession(false));
        ExperimentDetailsResponse response = ExperimentDetailsResponse.from(
                pipeline.execute(plans.create(request.toCommand())));
        ownership.assign(response.experimentId(), account.id(), clock.instant());
        return ResponseEntity.created(URI.create("/api/v1/experiments/" + response.experimentId()))
                .body(response);
    }

    @GetMapping("/mine")
    public List<ExperimentDetailsResponse> mine(HttpServletRequest request) {
        AuthenticatedAccount account = AuthenticatedAccount.require(request.getSession(false));
        return ownership.findExperimentIds(account.id()).stream()
                .map(pipeline::details)
                .map(ExperimentDetailsResponse::from)
                .toList();
    }

    @GetMapping("/{experimentId}")
    public ExperimentDetailsResponse details(@PathVariable UUID experimentId, HttpServletRequest request) {
        requireOwnerWhenPresent(experimentId, request);
        return ExperimentDetailsResponse.from(pipeline.details(experimentId));
    }

    @GetMapping("/{experimentId}/provenance")
    public ExperimentProvenanceResponse provenance(@PathVariable UUID experimentId, HttpServletRequest request) {
        requireOwnerWhenPresent(experimentId, request);
        return ExperimentProvenanceResponse.from(pipeline.provenance(experimentId));
    }

    @PostMapping("/{experimentId}/rerun")
    public RerunResponse rerun(@PathVariable UUID experimentId, HttpServletRequest request) {
        requireOwnerWhenPresent(experimentId, request);
        return RerunResponse.from(pipeline.rerun(experimentId));
    }

    private void requireOwnerWhenPresent(UUID experimentId, HttpServletRequest request) {
        ownership.ownerOf(experimentId).ifPresent(owner -> {
            if (!owner.equals(AuthenticatedAccount.require(request.getSession(false)).id())) {
                throw new ExperimentNotFoundException(experimentId);
            }
        });
    }
}
