package com.cryptolab.infrastructure.news.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerNewsTelemetryTest {

    @Test
    void exposesRequiredFailureAndDurationMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNewsTelemetry telemetry = new MicrometerNewsTelemetry(registry);

        telemetry.collectionFailed(new IllegalStateException("provider offline"));
        telemetry.inferenceFailed("news-1", 2, new IllegalStateException("model offline"));
        telemetry.inferenceCompleted(Duration.ofMillis(5));

        assertThat(registry.get("crypto.news.collection.failures").counter().count()).isOne();
        assertThat(registry.get("crypto.sentiment.inference.failures").counter().count()).isOne();
        assertThat(registry.get("crypto.sentiment.inference.duration").timer().count()).isOne();
    }
}
