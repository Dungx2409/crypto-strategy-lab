package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.port.NewsTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class MicrometerNewsTelemetry implements NewsTelemetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerNewsTelemetry.class);

    private final Counter collectionFailures;
    private final Counter inferenceFailures;
    private final Timer inferenceDuration;

    public MicrometerNewsTelemetry(MeterRegistry registry) {
        collectionFailures = Counter.builder("crypto.news.collection.failures")
                .description("News-provider collection failures")
                .register(registry);
        inferenceFailures = Counter.builder("crypto.sentiment.inference.failures")
                .description("Sentiment items that exhausted bounded inference retries")
                .register(registry);
        inferenceDuration = Timer.builder("crypto.sentiment.inference.duration")
                .description("Successful sentiment inference duration")
                .register(registry);
    }

    @Override
    public void collectionFailed(Throwable failure) {
        collectionFailures.increment();
        LOGGER.warn("news_collection_failed provider=cryptocompare errorType={} message={}",
                failure.getClass().getSimpleName(), safeMessage(failure));
    }

    @Override
    public void inferenceFailed(String newsId, int attempts, Throwable failure) {
        inferenceFailures.increment();
        LOGGER.warn("sentiment_inference_failed newsId={} attempts={} errorType={} message={}",
                newsId, attempts, failure.getClass().getSimpleName(), safeMessage(failure));
    }

    @Override
    public void inferenceCompleted(Duration duration) {
        inferenceDuration.record(duration);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
