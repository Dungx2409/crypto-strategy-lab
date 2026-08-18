package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.SearchRunSummary;

@FunctionalInterface
public interface SearchProgressPublisher {

    void publish(SearchRunSummary summary);

    static SearchProgressPublisher noop() {
        return summary -> {};
    }
}
