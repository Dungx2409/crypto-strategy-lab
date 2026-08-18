package com.cryptolab.api.marketdata;

import com.cryptolab.marketdata.application.MarketDataService;
import com.cryptolab.marketdata.application.MarketDataStreamService;
import com.cryptolab.marketdata.application.MarketStreamRegistration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
final class MarketSubscriptionTracker implements ChannelInterceptor {

    private static final String PREFIX = "/topic/market/";

    private final MarketDataService marketDataService;
    private final ObjectProvider<MarketDataStreamService> streamServiceProvider;
    private final Map<String, Map<String, MarketStreamRegistration>> sessions =
            new ConcurrentHashMap<>();

    MarketSubscriptionTracker(
            MarketDataService marketDataService,
            ObjectProvider<MarketDataStreamService> streamServiceProvider) {
        this.marketDataService = marketDataService;
        this.streamServiceProvider = streamServiceProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SUBSCRIBE) {
            subscribe(accessor);
        } else if (command == StompCommand.UNSUBSCRIBE) {
            unsubscribe(accessor);
        } else if (command == StompCommand.DISCONNECT) {
            disconnect(accessor.getSessionId());
        }
        return message;
    }

    private void subscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(PREFIX)) {
            return;
        }
        String[] parts = destination.substring(PREFIX.length()).split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid market topic: " + destination);
        }
        String sessionId = required(accessor.getSessionId(), "session id");
        String subscriptionId = required(accessor.getSubscriptionId(), "subscription id");
        var pair = marketDataService.validatedPair(parts[0]);
        var timeframe = marketDataService.validatedTimeframe(parts[1]);
        MarketStreamRegistration registration = streamServiceProvider.getObject().open(pair, timeframe);
        MarketStreamRegistration previous = sessions
                .computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .put(subscriptionId, registration);
        if (previous != null) {
            previous.close();
        }
    }

    private void unsubscribe(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        Map<String, MarketStreamRegistration> subscriptions = sessions.get(sessionId);
        if (subscriptions == null) {
            return;
        }
        MarketStreamRegistration registration = subscriptions.remove(subscriptionId);
        if (registration != null) {
            registration.close();
        }
        if (subscriptions.isEmpty()) {
            sessions.remove(sessionId, subscriptions);
        }
    }

    private void disconnect(String sessionId) {
        if (sessionId == null) {
            return;
        }
        Map<String, MarketStreamRegistration> registrations = sessions.remove(sessionId);
        if (registrations != null) {
            registrations.values().forEach(MarketStreamRegistration::close);
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing STOMP " + label);
        }
        return value;
    }
}
