package com.cryptolab.infrastructure.experiment.messaging;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "crypto.backtest.messaging-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BacktestJobMessagingConfiguration {

    @Bean
    Declarables backtestJobTopology() {
        DirectExchange jobs = new DirectExchange(BacktestJobTopology.JOB_EXCHANGE, true, false);
        DirectExchange deadLetters =
                new DirectExchange(BacktestJobTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue jobQueue = QueueBuilder.durable(BacktestJobTopology.JOB_QUEUE)
                .deadLetterExchange(BacktestJobTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(BacktestJobTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(BacktestJobTopology.DEAD_LETTER_QUEUE).build();
        Binding jobsBinding = BindingBuilder.bind(jobQueue)
                .to(jobs)
                .with(BacktestJobTopology.JOB_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetters)
                .with(BacktestJobTopology.DEAD_LETTER_ROUTING_KEY);
        TopicExchange domainEvents = new TopicExchange(DomainEventTopology.EXCHANGE, true, false);
        DirectExchange domainDeadLetters =
                new DirectExchange(DomainEventTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue evaluationQueue = QueueBuilder.durable(DomainEventTopology.EVALUATION_QUEUE)
                .deadLetterExchange(DomainEventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DomainEventTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue rankingQueue = QueueBuilder.durable(DomainEventTopology.RANKING_QUEUE)
                .withArgument("x-single-active-consumer", true)
                .deadLetterExchange(DomainEventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DomainEventTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue searchProgressQueue = QueueBuilder.durable(DomainEventTopology.SEARCH_PROGRESS_QUEUE)
                .deadLetterExchange(DomainEventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DomainEventTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue leaderboardQueue = QueueBuilder.durable(DomainEventTopology.LEADERBOARD_QUEUE)
                .deadLetterExchange(DomainEventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DomainEventTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue domainDeadLetterQueue =
                QueueBuilder.durable(DomainEventTopology.DEAD_LETTER_QUEUE).build();
        Binding evaluationBinding = BindingBuilder.bind(evaluationQueue)
                .to(domainEvents)
                .with(DomainEventTopology.BACKTEST_COMPLETED_ROUTING_KEY);
        Binding rankingBinding = BindingBuilder.bind(rankingQueue)
                .to(domainEvents)
                .with(DomainEventTopology.STRATEGY_EVALUATED_ROUTING_KEY);
        Binding searchProgressBinding = BindingBuilder.bind(searchProgressQueue)
                .to(domainEvents)
                .with(DomainEventTopology.STRATEGY_EVALUATED_ROUTING_KEY);
        Binding leaderboardBinding = BindingBuilder.bind(leaderboardQueue)
                .to(domainEvents)
                .with(DomainEventTopology.LEADERBOARD_UPDATED_ROUTING_KEY);
        Binding domainDeadLetterBinding = BindingBuilder.bind(domainDeadLetterQueue)
                .to(domainDeadLetters)
                .with(DomainEventTopology.DEAD_LETTER_ROUTING_KEY);
        return new Declarables(
                jobs,
                deadLetters,
                domainEvents,
                domainDeadLetters,
                jobQueue,
                deadLetterQueue,
                evaluationQueue,
                rankingQueue,
                searchProgressQueue,
                leaderboardQueue,
                domainDeadLetterQueue,
                jobsBinding,
                deadLetterBinding,
                evaluationBinding,
                rankingBinding,
                searchProgressBinding,
                leaderboardBinding,
                domainDeadLetterBinding);
    }

    @Bean
    @ConditionalOnProperty(name = "crypto.backtest.dispatch.publisher-enabled", havingValue = "true")
    RabbitBacktestJobOutboxPublisher backtestJobOutboxPublisher(
            JdbcBacktestJobOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            Clock marketDataClock,
            @Value("${crypto.backtest.dispatch.publisher-id:${spring.application.name}:unknown}") String publisherId,
            @Value("${crypto.backtest.dispatch.batch-size:50}") int batchSize,
            @Value("${crypto.backtest.dispatch.confirm-timeout:5s}") Duration confirmTimeout) {
        String uniquePublisherId = publisherId.replace("unknown", UUID.randomUUID().toString());
        return new RabbitBacktestJobOutboxPublisher(
                repository,
                rabbitTemplate,
                marketDataClock,
                uniquePublisherId,
                batchSize,
                confirmTimeout);
    }

    @Bean
    @ConditionalOnProperty(name = "crypto.domain-events.publisher-enabled", havingValue = "true")
    RabbitDomainEventOutboxPublisher domainEventOutboxPublisher(
            JdbcDomainEventOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            Clock marketDataClock,
            @Value("${spring.application.name:crypto-lab}") String applicationName,
            @Value("${crypto.domain-events.batch-size:50}") int batchSize,
            @Value("${crypto.domain-events.confirm-timeout:5s}") Duration confirmTimeout) {
        return new RabbitDomainEventOutboxPublisher(
                repository,
                rabbitTemplate,
                marketDataClock,
                applicationName + ":domain-events:" + UUID.randomUUID(),
                batchSize,
                confirmTimeout);
    }
}
