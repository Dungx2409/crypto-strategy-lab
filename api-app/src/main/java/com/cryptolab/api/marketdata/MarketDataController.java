package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.application.MarketDataService;
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
            @RequestParam(defaultValue = "500") int limit) {
        return MarketCandlesResponse.from(marketDataService.candles(symbol, timeframe, limit));
    }
}
