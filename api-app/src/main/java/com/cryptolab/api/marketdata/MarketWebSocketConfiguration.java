package com.cryptolab.api.marketdata;

import com.cryptolab.api.search.SearchSubscriptionAuthorizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
class MarketWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final MarketSubscriptionTracker subscriptionTracker;
    private final SearchSubscriptionAuthorizer searchSubscriptionAuthorizer;

    MarketWebSocketConfiguration(
            MarketSubscriptionTracker subscriptionTracker,
            SearchSubscriptionAuthorizer searchSubscriptionAuthorizer) {
        this.subscriptionTracker = subscriptionTracker;
        this.searchSubscriptionAuthorizer = searchSubscriptionAuthorizer;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionTracker, searchSubscriptionAuthorizer);
    }
}
