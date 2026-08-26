package com.cryptolab.api.news;

import com.cryptolab.news.application.CrawlerSourceService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/crawler-sources")
public final class CrawlerSourceController {

    private final CrawlerSourceService service;

    public CrawlerSourceController(CrawlerSourceService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CrawlerSourceResponse> create(@RequestBody CrawlerSourceRequest request) {
        var source = service.create(request.toDefinition());
        return ResponseEntity.created(URI.create("/api/v1/admin/crawler-sources/" + source.id()))
                .body(CrawlerSourceResponse.from(source));
    }

    @GetMapping
    List<CrawlerSourceResponse> list() {
        return service.list().stream().map(CrawlerSourceResponse::from).toList();
    }

    @PutMapping("/{sourceId}")
    CrawlerSourceResponse update(
            @PathVariable UUID sourceId,
            @RequestParam int expectedVersion,
            @RequestBody CrawlerSourceRequest request) {
        return CrawlerSourceResponse.from(service.update(sourceId, request.toDefinition(), expectedVersion));
    }
}
