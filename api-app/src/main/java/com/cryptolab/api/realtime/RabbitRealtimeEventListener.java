package com.cryptolab.api.realtime;

import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.domain.StrategyEvaluatedEvent;
import com.cryptolab.experiment.port.LeaderboardUpdatePublisher;
import com.cryptolab.experiment.port.SearchProgressPublisher;
import com.cryptolab.infrastructure.experiment.messaging.DomainEventTopology;
import com.cryptolab.infrastructure.experiment.messaging.JdbcProcessedEventRepository;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public final class RabbitRealtimeEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitRealtimeEventListener.class);

    static final String SEARCH_CONSUMER = "search-progress-websocket";
    static final String LEADERBOARD_CONSUMER = "leaderboard-websocket";

    private final SearchCoordinator searchCoordinator;
    private final SearchProgressPublisher searchPublisher;
    private final LeaderboardUpdatePublisher leaderboardPublisher;
    private final JdbcProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RabbitRealtimeEventListener(
            SearchCoordinator searchCoordinator,
            SearchProgressPublisher searchPublisher,
            LeaderboardUpdatePublisher leaderboardPublisher,
            JdbcProcessedEventRepository processedEvents,
            ObjectMapper objectMapper,
            Clock marketDataClock) {
        this.searchCoordinator = searchCoordinator;
        this.searchPublisher = searchPublisher;
        this.leaderboardPublisher = leaderboardPublisher;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
        this.clock = marketDataClock;
    }

    @RabbitListener(
            queues = DomainEventTopology.SEARCH_PROGRESS_QUEUE,
            containerFactory = "realtimeManualAckContainerFactory")
    public void receiveSearchProgress(Message message, Channel channel) throws IOException {
        receive(
                message,
                channel,
                DomainEventTopology.STRATEGY_EVALUATED_EVENT_TYPE,
                StrategyEvaluatedEvent.class,
                SEARCH_CONSUMER,
                event -> searchPublisher.publish(
                        searchCoordinator.details(event.payload().searchRunId())));
    }

    @RabbitListener(
            queues = DomainEventTopology.LEADERBOARD_QUEUE,
            containerFactory = "realtimeManualAckContainerFactory")
    public void receiveLeaderboard(Message message, Channel channel) throws IOException {
        receive(
                message,
                channel,
                DomainEventTopology.LEADERBOARD_UPDATED_EVENT_TYPE,
                LeaderboardUpdatedEvent.class,
                LEADERBOARD_CONSUMER,
                event -> leaderboardPublisher.publish(event.payload()));
    }

    private <T> void receive(
            Message message,
            Channel channel,
            String expectedEventType,
            Class<T> payloadType,
            String consumerName,
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
            LOGGER.warn("realtime_event_poison eventId={} eventType={} consumer={} errorType={}",
                    message.getMessageProperties().getMessageId(),
                    message.getMessageProperties().getHeaders().get("eventType"),
                    consumerName,
                    poison.getClass().getSimpleName());
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            LOGGER.info(
                    "realtime_event_received correlationId={} experimentId={} eventId={} eventType={} consumer={}",
                    event.correlationId(),
                    event.aggregateId(),
                    event.eventId(),
                    event.eventType(),
                    consumerName);
            if (!processedEvents.isProcessed(consumerName, event.eventId())) {
                handler.process(event);
                processedEvents.markProcessed(consumerName, event.eventId(), clock.instant());
            }
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException transientFailure) {
            LOGGER.error(
                    "realtime_event_failed correlationId={} experimentId={} eventId={} eventType={} consumer={} errorType={}",
                    event.correlationId(),
                    event.aggregateId(),
                    event.eventId(),
                    event.eventType(),
                    consumerName,
                    transientFailure.getClass().getSimpleName(),
                    transientFailure);
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
