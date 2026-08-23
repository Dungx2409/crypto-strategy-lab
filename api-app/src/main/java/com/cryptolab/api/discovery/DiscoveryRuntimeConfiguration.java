package com.cryptolab.api.discovery;

import com.cryptolab.experiment.application.ContinuousDiscoveryService;
import com.cryptolab.experiment.application.MarketDatasetService;
import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.port.DiscoveryScheduleRepository;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class DiscoveryRuntimeConfiguration {

    @Bean
    ContinuousDiscoveryService continuousDiscoveryService(
            DiscoveryScheduleRepository schedules,
            MarketDataProvider marketData,
            MarketDatasetService datasets,
            SearchCoordinator searches,
            StrategyRegistry strategies,
            Clock marketDataClock) {
        return new ContinuousDiscoveryService(
                schedules, marketData, datasets, searches, strategies, marketDataClock, UUID::randomUUID);
    }
}
