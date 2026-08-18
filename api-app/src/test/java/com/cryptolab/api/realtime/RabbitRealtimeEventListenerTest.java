package com.cryptolab.api.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptolab.experiment.domain.EvaluationMetrics;
import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.domain.Ranking;
import com.cryptolab.experiment.port.LeaderboardUpdatePublisher;
import com.cryptolab.experiment.port.SearchProgressPublisher;
import com.cryptolab.infrastructure.experiment.messaging.JdbcProcessedEventRepository;
import com.cryptolab.shared.domain.DomainEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitRealtimeEventListenerTest {

    private static final UUID SEARCH_ID = UUID.fromString("75000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("75000000-0000-0000-0000-000000000002");
    private static final long DELIVERY_TAG = 91L;

    @Test
    void duplicateLeaderboardEventIsAcknowledgedWithoutDuplicateWebsocketUpdate() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JdbcProcessedEventRepository processed = mock(JdbcProcessedEventRepository.class);
        when(processed.isProcessed("leaderboard-websocket", EVENT_ID)).thenReturn(false, true);
        AtomicInteger updates = new AtomicInteger();
        LeaderboardUpdatePublisher leaderboard = event -> updates.incrementAndGet();
        SearchProgressPublisher search = summary -> {};
        RabbitRealtimeEventListener listener = new RabbitRealtimeEventListener(
                null,
                search,
                leaderboard,
                processed,
                mapper,
                Clock.fixed(Instant.parse("2026-08-18T17:00:00Z"), ZoneOffset.UTC));
        Channel channel = mock(Channel.class);
        Message message = leaderboardMessage(mapper);

        listener.receiveLeaderboard(message, channel);
        listener.receiveLeaderboard(message, channel);

        assertThat(updates).hasValue(1);
        verify(processed).markProcessed(
                "leaderboard-websocket", EVENT_ID, Instant.parse("2026-08-18T17:00:00Z"));
        verify(channel, org.mockito.Mockito.times(2)).basicAck(DELIVERY_TAG, false);
    }

    private static Message leaderboardMessage(ObjectMapper mapper) throws Exception {
        EvaluationMetrics metrics =
                new EvaluationMetrics(BigDecimal.ONE, BigDecimal.ZERO, 1, BigDecimal.ONE);
        LeaderboardUpdatedEvent payload = new LeaderboardUpdatedEvent(
                SEARCH_ID,
                List.of(new Ranking(1, UUID.randomUUID(), metrics)),
                Instant.parse("2026-08-18T17:00:00Z"));
        DomainEventEnvelope<LeaderboardUpdatedEvent> envelope = new DomainEventEnvelope<>(
                EVENT_ID,
                "LeaderboardUpdated",
                1,
                payload.updatedAt(),
                "SearchRun",
                SEARCH_ID,
                SEARCH_ID.toString(),
                UUID.randomUUID().toString(),
                SEARCH_ID.toString(),
                payload);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setHeader("eventType", "LeaderboardUpdated");
        return new Message(mapper.writeValueAsBytes(envelope), properties);
    }
}
