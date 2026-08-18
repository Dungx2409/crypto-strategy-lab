package com.cryptolab.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cryptolab.experiment.domain.BacktestCompletedEvent;
import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.EventProcessingOutcome;
import com.cryptolab.experiment.port.BacktestCompletedEventProcessor;
import com.cryptolab.experiment.port.StrategyEvaluatedEventProcessor;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitDomainEventListenerTest {

    private static final long DELIVERY_TAG = 84L;
    private static final UUID EXPERIMENT_ID =
            UUID.fromString("73000000-0000-0000-0000-000000000001");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void acknowledgesDuplicateAfterTransactionalProcessorReturns() throws Exception {
        AtomicBoolean returned = new AtomicBoolean();
        BacktestCompletedEventProcessor evaluation = event -> {
            returned.set(true);
            return EventProcessingOutcome.DUPLICATE;
        };
        Channel channel = mock(Channel.class);

        listener(evaluation).receiveBacktestCompleted(validBacktestMessage(), channel);

        assertThat(returned).isTrue();
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, true);
    }

    @Test
    void rejectsMalformedOrMislabeledDomainEventToDlq() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        BacktestCompletedEventProcessor evaluation = event -> {
            invoked.set(true);
            return EventProcessingOutcome.PROCESSED;
        };
        Channel channel = mock(Channel.class);
        MessageProperties properties = properties("StrategyEvaluated");
        Message message = new Message("not-json".getBytes(), properties);

        listener(evaluation).receiveBacktestCompleted(message, channel);

        assertThat(invoked).isFalse();
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void requeuesInfrastructureFailureWithoutAcknowledging() throws Exception {
        BacktestCompletedEventProcessor evaluation = event -> {
            throw new IllegalStateException("database unavailable");
        };
        Channel channel = mock(Channel.class);

        listener(evaluation).receiveBacktestCompleted(validBacktestMessage(), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    private RabbitDomainEventListener listener(BacktestCompletedEventProcessor evaluation) {
        StrategyEvaluatedEventProcessor ranking = event -> EventProcessingOutcome.PROCESSED;
        return new RabbitDomainEventListener(evaluation, ranking, objectMapper);
    }

    private Message validBacktestMessage() throws Exception {
        Instant completedAt = Instant.parse("2026-08-18T15:00:00Z");
        BacktestCompletedEvent payload = new BacktestCompletedEvent(
                EXPERIMENT_ID,
                UUID.fromString("73000000-0000-0000-0000-000000000002"),
                new EvaluationMetrics(BigDecimal.ONE, BigDecimal.ZERO, 1, BigDecimal.ONE),
                "evaluator-v1",
                completedAt);
        DomainEventEnvelope<BacktestCompletedEvent> event = new DomainEventEnvelope<>(
                UUID.fromString("73000000-0000-0000-0000-000000000003"),
                "BacktestCompleted",
                1,
                completedAt,
                "Experiment",
                EXPERIMENT_ID,
                "correlation",
                null,
                EXPERIMENT_ID.toString(),
                payload);
        return new Message(objectMapper.writeValueAsBytes(event), properties("BacktestCompleted"));
    }

    private static MessageProperties properties(String eventType) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setHeader("eventType", eventType);
        return properties;
    }
}
