package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.MarketDatasetService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets")
public final class MarketDatasetController {

    private final MarketDatasetService service;

    public MarketDatasetController(MarketDatasetService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetReferenceResponse materialize(@RequestBody MarketDatasetRequest request) {
        return DatasetReferenceResponse.from(request.materialize(service).reference());
    }
}
