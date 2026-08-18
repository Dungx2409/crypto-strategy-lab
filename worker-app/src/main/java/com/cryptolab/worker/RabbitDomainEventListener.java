package com.cryptolab.worker;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.experiment.port.BacktestCompletedEventProcessor;
import com.cryptolab.experiment.port.StrategyEvaluatedEventProcessor;
import com.cryptolab.infrastructure.experiment.messaging.DomainEventTopology;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public final class RabbitDomainEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitDomainEventListener.class);

    private final BacktestCompletedEventProcessor evaluationProcessor;
    private final StrategyEvaluatedEventProcessor rankingProcessor;
    private final ObjectMapper objectMapper;

    public RabbitDomainEventListener(
            BacktestCompletedEventProcessor evaluationProcessor,
            StrategyEvaluatedEventProcessor rankingProcessor,
            ObjectMapper objectMapper) {
        this.evaluationProcessor = evaluationProcessor;
        this.rankingProcessor = rankingProcessor;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(
            queues = DomainEventTopology.EVALUATION_QUEUE,
            containerFactory = "domainEventManualAckContainerFactory")
    public void receiveBacktestCompleted(Message message, Channel channel) throws IOException {
        receive(
                message,
                channel,
                DomainEventTopology.BACKTEST_COMPLETED_EVENT_TYPE,
                BacktestCompletedEvent.class,
                evaluationProcessor::process);
    }

    @RabbitListener(
            queues = DomainEventTopology.RANKING_QUEUE,
            containerFactory = "domainEventManualAckContainerFactory")
    public void receiveStrategyEvaluated(Message message, Channel channel) throws IOException {
        receive(
                message,
                channel,
                DomainEventTopology.STRATEGY_EVALUATED_EVENT_TYPE,
                StrategyEvaluatedEvent.class,
                rankingProcessor::process);
    }

    private <T> void receive(
            Message message,
            Channel channel,
            String expectedEventType,
            Class<T> payloadType,
            EventHandler<T> handler)
            throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        DomainEventEnvelope<T> event;
        try {
            validateEventTypeHeader(message, expectedEventType);
            JavaType envelopeType = objectMapper
                    .getTypeFactory()
                    .constructParametricType(DomainEventEnvelope.class, payloadType);
            event = objectMapper.readValue(message.getBody(), envelopeType);
            if (!expectedEventType.equals(event.eventType())) {
                throw new IllegalArgumentException("eventType does not match payload");
            }
        } catch (JsonProcessingException | IllegalArgumentException poison) {
            LOGGER.warn("domain_event_poison eventId={} eventType={} errorType={}",
                    message.getMessageProperties().getMessageId(),
                    message.getMessageProperties().getHeaders().get("eventType"),
                    poison.getClass().getSimpleName());
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            LOGGER.info(
                    "domain_event_received correlationId={} experimentId={} eventId={} eventType={}",
                    event.correlationId(), event.aggregateId(), event.eventId(), event.eventType());
            handler.process(event);
            channel.basicAck(deliveryTag, false);
        } catch (IllegalArgumentException poison) {
            LOGGER.warn(
                    "domain_event_rejected correlationId={} experimentId={} eventId={} eventType={} errorType={}",
                    event.correlationId(),
                    event.aggregateId(),
                    event.eventId(),
                    event.eventType(),
                    poison.getClass().getSimpleName());
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException infrastructureFailure) {
            LOGGER.error(
                    "domain_event_failed correlationId={} experimentId={} eventId={} eventType={} errorType={}",
                    event.correlationId(),
                    event.aggregateId(),
                    event.eventId(),
                    event.eventType(),
                    infrastructureFailure.getClass().getSimpleName(),
                    infrastructureFailure);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private static void validateEventTypeHeader(Message message, String expectedEventType) {
        Object header = message.getMessageProperties().getHeaders().get("eventType");
        if (header == null || !expectedEventType.equals(header.toString())) {
            throw new IllegalArgumentException("eventType header is missing or unexpected");
        }
    }

    @FunctionalInterface
    private interface EventHandler<T> {
        void process(DomainEventEnvelope<T> event);
    }
}
