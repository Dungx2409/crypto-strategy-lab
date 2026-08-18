package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.SearchRunStatus;
import java.util.UUID;

public interface SearchTelemetry {

    void runStarted(UUID searchRunId, String generatorType);

    void candidatesGenerated(UUID searchRunId, long count);

    void runFinished(UUID searchRunId, SearchRunStatus status);

    static SearchTelemetry noop() {
        return new SearchTelemetry() {
            @Override
            public void runStarted(UUID searchRunId, String generatorType) {}

            @Override
            public void candidatesGenerated(UUID searchRunId, long count) {}

            @Override
            public void runFinished(UUID searchRunId, SearchRunStatus status) {}
        };
    }
}
