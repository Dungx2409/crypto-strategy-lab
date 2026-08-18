package com.cryptolab.news.port;

import java.time.Duration;

public interface NewsTelemetry {

    void collectionFailed(Throwable failure);

    void inferenceFailed(String newsId, int attempts, Throwable failure);

    void inferenceCompleted(Duration duration);

    static NewsTelemetry noop() {
        return new NewsTelemetry() {
            @Override
            public void collectionFailed(Throwable failure) {}

            @Override
            public void inferenceFailed(String newsId, int attempts, Throwable failure) {}

            @Override
            public void inferenceCompleted(Duration duration) {}
        };
    }
}
