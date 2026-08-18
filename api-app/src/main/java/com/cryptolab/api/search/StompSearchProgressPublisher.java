package com.cryptolab.api.search;

import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.port.SearchProgressPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public final class StompSearchProgressPublisher implements SearchProgressPublisher {

    public static final String TOPIC_PREFIX = "/topic/search/";

    private final SimpMessagingTemplate messagingTemplate;

    public StompSearchProgressPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(SearchRunSummary summary) {
        messagingTemplate.convertAndSend(
                TOPIC_PREFIX + summary.run().id(), SearchRunResponse.from(summary));
    }
}
