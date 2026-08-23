package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.shared.domain.SentimentObservation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MarketDataset(
        UUID id,
        MarketDatasetRef reference,
        List<Candle> candles,
        List<SentimentObservation> sentimentObservations) {

    public MarketDataset(UUID id, MarketDatasetRef reference, List<Candle> candles) {
        this(id, reference, candles, List.of());
    }

    public MarketDataset {
        Objects.requireNonNull(id, "dataset id must not be null");
        Objects.requireNonNull(reference, "dataset reference must not be null");
        candles = List.copyOf(Objects.requireNonNull(candles, "dataset candles must not be null"));
        sentimentObservations = List.copyOf(Objects.requireNonNull(
                sentimentObservations, "sentimentObservations must not be null"));
        if (candles.isEmpty()) {
            throw new IllegalArgumentException("dataset must contain at least one candle");
        }

        Instant previous = null;
        for (Candle candle : candles) {
            if (!reference.symbol().equals(candle.symbol()) || reference.timeframe() != candle.timeframe()) {
                throw new IllegalArgumentException("dataset candles must match symbol and timeframe");
            }
            if (candle.openTime().isBefore(reference.from()) || !candle.openTime().isBefore(reference.to())) {
                throw new IllegalArgumentException("dataset candle lies outside the referenced range");
            }
            if (previous != null && !candle.openTime().isAfter(previous)) {
                throw new IllegalArgumentException("dataset candles must be strictly ordered");
            }
            previous = candle.openTime();
        }
        Instant previousObservation = null;
        for (SentimentObservation observation : sentimentObservations) {
            if (observation.observedAt().isAfter(reference.to())) {
                throw new IllegalArgumentException("sentiment observation lies after the referenced range");
            }
            if (previousObservation != null && observation.observedAt().isBefore(previousObservation)) {
                throw new IllegalArgumentException("sentiment observations must be ordered");
            }
            previousObservation = observation.observedAt();
        }
        String calculatedChecksum = MarketDatasetChecksum.calculate(candles, sentimentObservations);
        if (!reference.checksum().equals(calculatedChecksum)) {
            throw new IllegalArgumentException("dataset checksum does not match its inputs");
        }
    }
}
