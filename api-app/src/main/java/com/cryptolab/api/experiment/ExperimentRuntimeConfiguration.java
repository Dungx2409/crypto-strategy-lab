package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.application.ExperimentPipelineService;
import com.cryptolab.experiment.application.ExperimentPlanFactory;
import com.cryptolab.experiment.application.MarketDatasetService;
import com.cryptolab.experiment.application.ManualRunService;
import com.cryptolab.experiment.port.BacktestPort;
import com.cryptolab.experiment.port.CandidateProvider;
import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.experiment.port.ExperimentEvaluator;
import com.cryptolab.experiment.port.ExperimentRepository;
import com.cryptolab.experiment.port.MarketDatasetProvider;
import com.cryptolab.experiment.port.MarketDatasetRepository;
import com.cryptolab.experiment.port.ManualRunRepository;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.strategy.port.StrategyRegistry;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ExperimentRuntimeConfiguration {

    @Bean
    ExperimentEvaluator experimentEvaluator() {
        return new DefaultExperimentEvaluator();
    }

    @Bean
    DefaultRankingService rankingService() {
        return new DefaultRankingService();
    }

    @Bean
    BacktestPort backtestPort(
            CandidateProvider candidates,
            MarketDatasetProvider datasets,
            StrategyRegistry strategies,
            CombinationPolicyResolver policies,
            Clock marketDataClock) {
        return new DeterministicBacktestEngine(
                candidates, datasets, strategies, policies, marketDataClock);
    }

    @Bean
    MarketDatasetService marketDatasetService(
            MarketDatasetRepository repository,
            Clock marketDataClock) {
        return new MarketDatasetService(repository, marketDataClock, UUID::randomUUID);
    }

    @Bean
    ExperimentPlanFactory experimentPlanFactory(
            ExperimentEvaluator evaluator,
            Clock marketDataClock,
            @Value("${crypto.provenance.git-commit:dev}") String codeCommit,
            @Value("${crypto.provenance.build-version:dev}") String buildVersion) {
        return new ExperimentPlanFactory(
                evaluator.version(), codeCommit, buildVersion, marketDataClock, UUID::randomUUID);
    }

    @Bean
    ExperimentPipelineService experimentPipelineService(
            BacktestPort backtest,
            ExperimentEvaluator evaluator,
            DefaultRankingService ranking,
            ExperimentRepository repository,
            Clock marketDataClock) {
        return new ExperimentPipelineService(
                backtest, evaluator, ranking, repository, marketDataClock, UUID::randomUUID);
    }

    @Bean
    ManualRunService manualRunService(
            ManualRunRepository manualRuns,
            UserStrategyRepository userStrategies,
            MarketDataProvider marketData,
            ExperimentPlanFactory plans,
            ExperimentPipelineService pipeline,
            Clock marketDataClock) {
        return new ManualRunService(
                manualRuns,
                userStrategies,
                marketData,
                plans,
                pipeline,
                marketDataClock,
                UUID::randomUUID);
    }
}
