package com.cryptolab.api.strategy;

import com.cryptolab.strategy.port.StrategyRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/strategies")
public final class StrategyController {

    private final StrategyRegistry registry;

    public StrategyController(StrategyRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<StrategyCatalogResponse> listAvailableStrategies() {
        return registry.availableStrategies().stream()
                .map(StrategyCatalogResponse::from)
                .toList();
    }
}
