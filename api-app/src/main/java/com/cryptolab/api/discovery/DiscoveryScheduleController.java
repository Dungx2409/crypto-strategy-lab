package com.cryptolab.api.discovery;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.ContinuousDiscoveryService;
import com.cryptolab.experiment.domain.DiscoverySchedule;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery-schedules")
public final class DiscoveryScheduleController {

    private final ContinuousDiscoveryService service;

    public DiscoveryScheduleController(ContinuousDiscoveryService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<DiscoverySchedule> create(
            @RequestBody DiscoveryScheduleRequest request, HttpServletRequest servletRequest) {
        DiscoverySchedule schedule = service.create(
                account(servletRequest).id(), request.symbol(), request.parsedTimeframe(),
                request.resolvedLookback(), request.resolvedCapital(),
                request.resolvedCandidateLimit(), request.resolvedInterval());
        return ResponseEntity.status(HttpStatus.CREATED).body(schedule);
    }

    @GetMapping
    List<DiscoverySchedule> list(HttpServletRequest request) {
        return service.list(account(request).id());
    }

    @GetMapping("/{scheduleId}")
    DiscoverySchedule get(@PathVariable UUID scheduleId, HttpServletRequest request) {
        return service.get(account(request).id(), scheduleId);
    }

    @PostMapping("/{scheduleId}/stop")
    DiscoverySchedule stop(@PathVariable UUID scheduleId, HttpServletRequest request) {
        return service.stop(account(request).id(), scheduleId);
    }

    @PostMapping("/{scheduleId}/start")
    DiscoverySchedule start(@PathVariable UUID scheduleId, HttpServletRequest request) {
        return service.start(account(request).id(), scheduleId);
    }

    @PutMapping("/{scheduleId}")
    DiscoverySchedule update(
            @PathVariable UUID scheduleId,
            @RequestBody DiscoveryScheduleRequest body,
            HttpServletRequest request) {
        return service.update(
                account(request).id(), scheduleId, body.symbol(), body.parsedTimeframe(),
                body.resolvedLookback(), body.resolvedCapital(),
                body.resolvedCandidateLimit(), body.resolvedInterval());
    }

    @GetMapping("/{scheduleId}/versions")
    java.util.List<com.cryptolab.experiment.domain.DiscoveryScheduleVersion> versions(
            @PathVariable UUID scheduleId, HttpServletRequest request) {
        return service.versions(account(request).id(), scheduleId);
    }

    private static AuthenticatedAccount account(HttpServletRequest request) {
        return AuthenticatedAccount.require(request.getSession(false));
    }
}
