package com.cryptolab.api.search;

import com.cryptolab.experiment.application.RandomStrategyGenerator;
import com.cryptolab.experiment.application.GeneticStrategyGenerator;
import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.domain.StopConditionEvaluator;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.port.ExperimentEvaluator;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.experiment.port.SearchProgressPublisher;
import com.cryptolab.experiment.port.SearchTelemetry;
import com.cryptolab.experiment.port.StrategyGenerator;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
class SearchRuntimeConfiguration {

    @Bean
    RandomStrategyGenerator randomStrategyGenerator(StrategyRegistry registry) {
        return new RandomStrategyGenerator(registry);
    }

    @Bean
    GeneticStrategyGenerator geneticStrategyGenerator(StrategyRegistry registry) {
        return new GeneticStrategyGenerator(registry);
    }

    @Bean
    StopConditionEvaluator stopConditionEvaluator() {
        return new StopConditionEvaluator();
    }

    @Bean
    SearchCoordinator searchCoordinator(
            List<StrategyGenerator> generators,
            SearchRunRepository repository,
            StopConditionEvaluator stopConditions,
            ExperimentEvaluator evaluator,
            SearchProgressPublisher progressPublisher,
            SearchTelemetry telemetry,
            Clock marketDataClock,
            @Value("${crypto.search.generator:random}") String defaultGeneratorType,
            @Value("${crypto.provenance.git-commit:dev}") String codeCommit,
            @Value("${crypto.provenance.build-version:dev}") String buildVersion) {
        return new SearchCoordinator(
                generators,
                defaultGeneratorType,
                repository,
                stopConditions,
                marketDataClock,
                new JobDispatchMetadata(evaluator.version(), codeCommit, buildVersion),
                progressPublisher,
                telemetry);
    }

    @Bean
    TaskExecutor searchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("search-generation-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        return executor;
    }
}
