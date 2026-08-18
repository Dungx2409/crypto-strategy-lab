package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.LeaderboardUpdatedEvent;

@FunctionalInterface
public interface LeaderboardUpdatePublisher {

    void publish(LeaderboardUpdatedEvent event);
}
