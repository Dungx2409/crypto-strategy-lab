package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.application.MarketDataService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market")
public final class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/candles")
    MarketCandlesResponse candles(
            @RequestParam String symbol,
            @RequestParam String timeframe,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        if (from != null || to != null) {
            return MarketCandlesResponse.from(marketDataService.candles(symbol, timeframe, from, to));
        }
        return MarketCandlesResponse.from(marketDataService.candles(symbol, timeframe, limit));
    }
}
