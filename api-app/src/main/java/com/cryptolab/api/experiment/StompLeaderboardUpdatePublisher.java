package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;
import com.cryptolab.experiment.port.LeaderboardUpdatePublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public final class StompLeaderboardUpdatePublisher implements LeaderboardUpdatePublisher {

    public static final String TOPIC_PREFIX = "/topic/leaderboard/";

    private final SimpMessagingTemplate messagingTemplate;

    public StompLeaderboardUpdatePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(LeaderboardUpdatedEvent event) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + event.searchRunId(), event);
    }
}
