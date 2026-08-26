package com.cryptolab.api.search;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search-runs")
public final class SearchRunController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchRunController.class);

    private final SearchCoordinator coordinator;
    private final Supplier<UUID> idGenerator;
    private final TaskExecutor executor;

    @Autowired
    public SearchRunController(SearchCoordinator coordinator, TaskExecutor searchTaskExecutor) {
        this(coordinator, UUID::randomUUID, searchTaskExecutor);
    }

    SearchRunController(
            SearchCoordinator coordinator,
            Supplier<UUID> idGenerator,
            TaskExecutor executor) {
        this.coordinator = coordinator;
        this.idGenerator = idGenerator;
        this.executor = executor;
    }

    @PostMapping
    public ResponseEntity<SearchRunResponse> start(
            @RequestBody SearchRunRequest request,
            @RequestParam(required = false) String generator,
            HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        var command = request.toCommand(idGenerator.get(), account.id());
        SearchRunResponse response = SearchRunResponse.from(coordinator.create(command, generator));
        executor.execute(() -> {
            LOGGER.info("search_async_dispatch correlationId={} searchRunId={} generatorType={}",
                    response.searchRunId(), response.searchRunId(), response.generatorType());
            try {
                coordinator.run(command);
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "search_async_failed correlationId={} searchRunId={} generatorType={} errorType={}",
                        response.searchRunId(),
                        response.searchRunId(),
                        response.generatorType(),
                        failure.getClass().getSimpleName(),
                        failure);
            }
        });
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/search-runs/" + response.searchRunId()))
                .body(response);
    }

    @GetMapping("/capabilities")
    public SearchCapabilitiesResponse capabilities() {
        return new SearchCapabilitiesResponse(
                coordinator.defaultGeneratorType(),
                coordinator.availableGeneratorTypes(),
                DeterministicBacktestEngine.VERSION,
                DeterministicBacktestEngine.FILL_POLICY);
    }

    @GetMapping
    public SearchRunHistoryResponse history(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        Cursor decoded = decode(cursor);
        return SearchRunHistoryResponse.from(
                coordinator.history(account.id(), decoded.createdAt(), decoded.id(), limit), limit);
    }

    @GetMapping("/{searchRunId}")
    public SearchRunResponse details(
            @PathVariable UUID searchRunId, HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        return SearchRunResponse.from(coordinator.details(account.id(), searchRunId));
    }

    @PostMapping("/{searchRunId}/cancel")
    public ResponseEntity<SearchRunResponse> cancel(
            @PathVariable UUID searchRunId, HttpServletRequest servletRequest) {
        AuthenticatedAccount account =
                AuthenticatedAccount.require(servletRequest.getSession(false));
        return ResponseEntity.accepted()
                .body(SearchRunResponse.from(coordinator.cancel(account.id(), searchRunId)));
    }

    private static Cursor decode(String value) {
        if (value == null || value.isBlank()) return new Cursor(null, null);
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            return new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(decoded.substring(0, separator))),
                    UUID.fromString(decoded.substring(separator + 1)));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("history cursor is invalid", invalid);
        }
    }

    private record Cursor(Instant createdAt, UUID id) {}
}
