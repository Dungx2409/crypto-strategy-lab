package com.cryptolab.infrastructure.experiment.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class RabbitDomainEventOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitDomainEventOutboxPublisher.class);

    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private final JdbcDomainEventOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final String publisherId;
    private final int batchSize;
    private final Duration confirmTimeout;

    public RabbitDomainEventOutboxPublisher(
            JdbcDomainEventOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            Clock clock,
            String publisherId,
            int batchSize,
            Duration confirmTimeout) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.publisherId = publisherId;
        this.batchSize = batchSize;
        this.confirmTimeout = confirmTimeout;
        this.rabbitTemplate.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${crypto.domain-events.poll-interval:500}")
    public void publishScheduledBatch() {
        publishAvailable();
    }

    public int publishAvailable() {
        List<BacktestJobOutboxMessage> messages =
                repository.claimBatch(publisherId, batchSize, CLAIM_LEASE, clock.instant());
        int confirmed = 0;
        for (BacktestJobOutboxMessage message : messages) {
            try {
                CorrelationData correlation = new CorrelationData(message.eventId().toString());
                rabbitTemplate.send(
                        message.destination(), message.routingKey(), message(message), correlation);
                CorrelationData.Confirm confirm = correlation.getFuture()
                        .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("broker nack: " + confirm.getReason());
                }
                if (correlation.getReturned() != null) {
                    throw new IllegalStateException("broker returned unroutable domain event");
                }
                repository.markConfirmed(
                        message.eventId(), message.eventType(), publisherId, clock.instant());
                confirmed++;
                LOGGER.info("domain_event_published eventId={} experimentId={} eventType={}",
                        message.eventId(), message.experimentId(), message.eventType());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                repository.recordFailure(
                        message.eventId(),
                        message.eventType(),
                        publisherId,
                        safeMessage(exception),
                        clock.instant().plusSeconds(1L << Math.min(message.attemptCount(), 6)));
                LOGGER.warn("domain_event_publish_failed eventId={} experimentId={} eventType={} errorType={}",
                        message.eventId(),
                        message.experimentId(),
                        message.eventType(),
                        exception.getClass().getSimpleName());
            }
        }
        return confirmed;
    }

    private static Message message(BacktestJobOutboxMessage outbox) {
        return MessageBuilder.withBody(outbox.payloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(outbox.eventId().toString())
                .setHeader("eventType", outbox.eventType())
                .setHeader("schemaVersion", outbox.schemaVersion())
                .setHeader("experimentId", outbox.experimentId().toString())
                .build();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
