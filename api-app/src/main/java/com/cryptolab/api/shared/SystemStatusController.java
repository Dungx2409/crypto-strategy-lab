package com.cryptolab.api.shared;

import com.cryptolab.marketdata.application.MarketDataStreamService;
import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.shared.port.OperationalStatusProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public final class SystemStatusController {

    private final MarketDataStreamService market;
    private final NewsCollector news;
    private final OperationalStatusProvider operations;

    public SystemStatusController(
            MarketDataStreamService market,
            NewsCollector news,
            OperationalStatusProvider operations) {
        this.market = market;
        this.news = news;
        this.operations = operations;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return SystemStatusResponse.from(market.healthStatus(), news.health(), operations.current());
    }
}
