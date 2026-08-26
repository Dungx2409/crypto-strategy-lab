package com.cryptolab.api.search;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.SearchCoordinator;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class SearchSubscriptionAuthorizer implements ChannelInterceptor {

    private static final String SEARCH = "/topic/search/";
    private static final String LEADERBOARD = "/topic/leaderboard/";

    private final SearchCoordinator searches;

    public SearchSubscriptionAuthorizer(SearchCoordinator searches) {
        this.searches = searches;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        String id = suffix(destination, SEARCH);
        if (id == null) id = suffix(destination, LEADERBOARD);
        if (id == null) return message;
        AuthenticatedAccount account = authenticatedAccount(accessor);
        if (account == null) {
            throw new IllegalArgumentException("Authentication is required for private run topics");
        }
        searches.details(account.id(), UUID.fromString(id));
        return message;
    }

    private static AuthenticatedAccount authenticatedAccount(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof AuthenticatedAccount account) return account;
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedAccount account) {
            return account;
        }
        return null;
    }

    private static String suffix(String destination, String prefix) {
        return destination != null && destination.startsWith(prefix)
                ? destination.substring(prefix.length())
                : null;
    }
}
