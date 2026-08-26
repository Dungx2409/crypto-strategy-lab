package com.cryptolab.api.strategy;

import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.strategy.application.StrategyAuthoringService;
import com.cryptolab.strategy.application.UserStrategyService;
import com.cryptolab.strategy.port.StrategyAuthoringModel;
import com.cryptolab.strategy.port.StrategyDocumentDecoder;
import com.cryptolab.strategy.port.StrategyRegistry;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StrategyAuthoringRuntimeConfiguration {

    @Bean
    StrategyAuthoringService strategyAuthoringService(
            StrategyAuthoringModel model,
            StrategyDocumentDecoder decoder,
            UserStrategyRepository repository,
            StrategyRegistry registry,
            CombinationPolicyResolver policyResolver,
            Clock marketDataClock) {
        return new StrategyAuthoringService(
                model, decoder, repository, registry, policyResolver, marketDataClock, UUID::randomUUID);
    }

    @Bean
    UserStrategyService userStrategyService(
            UserStrategyRepository repository,
            StrategyRegistry registry,
            CombinationPolicyResolver policyResolver,
            Clock marketDataClock) {
        return new UserStrategyService(
                repository, registry, policyResolver, marketDataClock, UUID::randomUUID);
    }
}
