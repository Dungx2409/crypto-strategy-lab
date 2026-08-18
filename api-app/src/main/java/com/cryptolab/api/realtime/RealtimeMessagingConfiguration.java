package com.cryptolab.api.realtime;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
class RealtimeMessagingConfiguration {

    @Bean
    SimpleRabbitListenerContainerFactory realtimeManualAckContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${crypto.domain-events.realtime.concurrency:1}") int concurrency,
            @Value("${crypto.domain-events.realtime.prefetch:10}") int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(concurrency);
        factory.setPrefetchCount(prefetch);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
