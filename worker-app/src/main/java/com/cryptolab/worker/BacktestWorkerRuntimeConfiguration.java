package com.cryptolab.worker;

import com.cryptolab.experiment.application.BacktestWorkerService;
import com.cryptolab.experiment.application.AsyncEvaluationService;
import com.cryptolab.experiment.application.AsyncRankingService;
import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.experiment.application.DefaultRankingService;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.port.BacktestPort;
import com.cryptolab.experiment.port.BacktestWorkerRepository;
import com.cryptolab.experiment.port.AsyncEvaluationRepository;
import com.cryptolab.experiment.port.AsyncRankingRepository;
import com.cryptolab.experiment.port.BacktestCompletedEventProcessor;
import com.cryptolab.experiment.port.CandidateProvider;
import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.experiment.port.ExperimentEvaluator;
import com.cryptolab.experiment.port.MarketDatasetProvider;
import com.cryptolab.experiment.port.StrategyEvaluatedEventProcessor;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
class BacktestWorkerRuntimeConfiguration {

    @Bean
    Clock workerClock() {
        return Clock.systemUTC();
    }

    @Bean
    ExperimentEvaluator workerExperimentEvaluator() {
        return new DefaultExperimentEvaluator();
    }

    @Bean
    BacktestPort workerBacktestPort(
            CandidateProvider candidates,
            MarketDatasetProvider datasets,
            StrategyRegistry strategies,
            CombinationPolicyResolver policies,
            Clock workerClock) {
        return new DeterministicBacktestEngine(
                candidates, datasets, strategies, policies, workerClock);
    }

    @Bean
    BacktestWorkerService backtestWorkerService(
            BacktestPort backtest,
            ExperimentEvaluator evaluator,
            BacktestWorkerRepository repository,
            Clock workerClock,
            @Value("${crypto.backtest.worker.claim-lease:5m}") Duration claimLease) {
        return new BacktestWorkerService(backtest, evaluator, repository, workerClock, claimLease);
    }

    @Bean
    BacktestCompletedEventProcessor backtestCompletedEventProcessor(
            AsyncEvaluationRepository repository, Clock workerClock) {
        return new AsyncEvaluationService(repository, workerClock);
    }

    @Bean
    StrategyEvaluatedEventProcessor strategyEvaluatedEventProcessor(
            AsyncRankingRepository repository, Clock workerClock) {
        return new AsyncRankingService(repository, new DefaultRankingService(), workerClock);
    }

    @Bean
    SimpleRabbitListenerContainerFactory backtestManualAckContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${crypto.backtest.worker.concurrency:1}") int concurrency,
            @Value("${crypto.backtest.worker.prefetch:1}") int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(concurrency);
        factory.setPrefetchCount(prefetch);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    SimpleRabbitListenerContainerFactory domainEventManualAckContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${crypto.domain-events.consumer.concurrency:1}") int concurrency,
            @Value("${crypto.domain-events.consumer.prefetch:10}") int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(concurrency);
        factory.setPrefetchCount(prefetch);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
