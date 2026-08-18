package com.cryptolab.infrastructure.experiment.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class RabbitBacktestJobOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitBacktestJobOutboxPublisher.class);

    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);

    private final JdbcBacktestJobOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final String publisherId;
    private final int batchSize;
    private final Duration confirmTimeout;

    public RabbitBacktestJobOutboxPublisher(
            JdbcBacktestJobOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            Clock clock,
            String publisherId,
            int batchSize,
            Duration confirmTimeout) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.publisherId = requireText(publisherId, "publisherId");
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        this.batchSize = batchSize;
        if (confirmTimeout == null || confirmTimeout.isZero() || confirmTimeout.isNegative()) {
            throw new IllegalArgumentException("confirmTimeout must be positive");
        }
        this.confirmTimeout = confirmTimeout;
        this.rabbitTemplate.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${crypto.backtest.dispatch.poll-interval:500ms}")
    public void publishScheduledBatch() {
        publishAvailable();
    }

    public int publishAvailable() {
        Instant claimedAt = clock.instant();
        List<BacktestJobOutboxMessage> messages =
                repository.claimBatch(publisherId, batchSize, CLAIM_LEASE, claimedAt);
        int confirmed = 0;
        for (BacktestJobOutboxMessage message : messages) {
            try {
                publishAndAwaitConfirm(message);
                boolean dispatched = repository.markConfirmed(
                        message.eventId(), message.eventType(), publisherId, clock.instant());
                if (dispatched) {
                    confirmed++;
                    LOGGER.info("backtest_job_published eventId={} experimentId={} jobId={} eventType={}",
                            message.eventId(), message.experimentId(), message.experimentId(), message.eventType());
                }
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                repository.recordFailure(
                        message.eventId(),
                        publisherId,
                        safeMessage(exception),
                        clock.instant().plus(retryDelay(message.attemptCount())));
                LOGGER.warn(
                        "backtest_job_publish_failed eventId={} experimentId={} jobId={} eventType={} errorType={}",
                        message.eventId(),
                        message.experimentId(),
                        message.experimentId(),
                        message.eventType(),
                        exception.getClass().getSimpleName());
            }
        }
        return confirmed;
    }

    private void publishAndAwaitConfirm(BacktestJobOutboxMessage outbox) throws Exception {
        CorrelationData correlation = new CorrelationData(outbox.eventId().toString());
        rabbitTemplate.send(
                outbox.destination(),
                outbox.routingKey(),
                message(outbox),
                correlation);
        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("broker nack: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("broker returned unroutable message: "
                    + correlation.getReturned().getReplyText());
        }
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

    private static Duration retryDelay(int previousAttempts) {
        long seconds = 1L << Math.min(previousAttempts, 6);
        return Duration.ofSeconds(Math.min(seconds, MAX_RETRY_DELAY.toSeconds()));
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
