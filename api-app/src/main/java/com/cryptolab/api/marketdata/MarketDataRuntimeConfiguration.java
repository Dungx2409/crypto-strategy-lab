package com.cryptolab.api.marketdata;

import com.cryptolab.infrastructure.marketdata.adapter.ExecutorMarketDataScheduler;
import com.cryptolab.marketdata.application.MarketDataService;
import com.cryptolab.marketdata.application.MarketDataStreamService;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.CandleUpdatePublisher;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.marketdata.port.MarketDataScheduler;
import com.cryptolab.marketdata.port.MarketDataTelemetry;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MarketDataRuntimeConfiguration {

    @Bean
    Clock marketDataClock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService marketDataReconnectExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "market-data-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    MarketDataScheduler marketDataScheduler(ScheduledExecutorService marketDataReconnectExecutor) {
        return new ExecutorMarketDataScheduler(marketDataReconnectExecutor);
    }

    @Bean
    MarketDataService marketDataService(
            MarketDataProvider provider,
            CandleStore store,
            Clock marketDataClock,
            @Value("${crypto.market.supported-symbols:BTCUSDT}") String supportedSymbols,
            @Value("${crypto.market.maximum-limit:1000}") int maximumLimit) {
        Set<String> symbols = Arrays.stream(supportedSymbols.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new MarketDataService(provider, store, marketDataClock, symbols, maximumLimit);
    }

    @Bean(destroyMethod = "close")
    MarketDataStreamService marketDataStreamService(
            MarketDataProvider provider,
            CandleStore store,
            CandleUpdatePublisher publisher,
            MarketDataScheduler scheduler,
            MarketDataTelemetry telemetry,
            Clock marketDataClock,
            @Value("${crypto.market.reconnect.initial-delay-ms:1000}") long initialDelayMs,
            @Value("${crypto.market.reconnect.max-delay-ms:30000}") long maximumDelayMs) {
        return new MarketDataStreamService(
                provider,
                store,
                publisher,
                scheduler,
                telemetry,
                marketDataClock,
                Duration.ofMillis(initialDelayMs),
                Duration.ofMillis(maximumDelayMs));
    }
}
