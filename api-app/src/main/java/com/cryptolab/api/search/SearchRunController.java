package com.cryptolab.api.search;

import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
            @RequestParam(required = false) String generator) {
        var command = request.toCommand(idGenerator.get());
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
    public List<SearchRunResponse> history(@RequestParam(defaultValue = "25") int limit) {
        return coordinator.history(limit).stream()
                .map(SearchRunResponse::from)
                .toList();
    }

    @GetMapping("/{searchRunId}")
    public SearchRunResponse details(@PathVariable UUID searchRunId) {
        return SearchRunResponse.from(coordinator.details(searchRunId));
    }

    @PostMapping("/{searchRunId}/cancel")
    public ResponseEntity<SearchRunResponse> cancel(@PathVariable UUID searchRunId) {
        return ResponseEntity.accepted().body(SearchRunResponse.from(coordinator.cancel(searchRunId)));
    }
}
