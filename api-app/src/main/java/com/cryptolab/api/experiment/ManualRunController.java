package com.cryptolab.api.experiment;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.ManualRunService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manual-runs")
public final class ManualRunController {

    private final ManualRunService service;
    private final TaskExecutor executor;

    public ManualRunController(
            ManualRunService service,
            @Qualifier("searchTaskExecutor") TaskExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @PostMapping
    ResponseEntity<ManualRunResponse> create(
            @RequestBody ManualRunRequest request, HttpServletRequest servletRequest) {
        UUID accountId = account(servletRequest).id();
        var batch = service.create(
                accountId,
                request.strategyId(),
                request.symbol(),
                request.parsedTimeframes(),
                request.from(),
                request.to(),
                request.resolvedExecutionConfig());
        executor.execute(() -> service.execute(batch.id()));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/manual-runs/" + batch.id()))
                .body(ManualRunResponse.from(batch));
    }

    @GetMapping
    List<ManualRunResponse> list(HttpServletRequest request) {
        return service.list(account(request).id()).stream().map(ManualRunResponse::from).toList();
    }

    @GetMapping("/{batchId}")
    ManualRunResponse get(@PathVariable UUID batchId, HttpServletRequest request) {
        return ManualRunResponse.from(service.get(account(request).id(), batchId));
    }

    @PostMapping("/{batchId}/cancel")
    ResponseEntity<ManualRunResponse> cancel(
            @PathVariable UUID batchId, HttpServletRequest request) {
        return ResponseEntity.accepted()
                .body(ManualRunResponse.from(service.cancel(account(request).id(), batchId)));
    }

    private static AuthenticatedAccount account(HttpServletRequest request) {
        return AuthenticatedAccount.require(request.getSession(false));
    }
}
