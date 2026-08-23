package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetChecksum;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.port.MarketDatasetRepository;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.shared.domain.SentimentObservation;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class MarketDatasetService {

    private final MarketDatasetRepository repository;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public MarketDatasetService(
            MarketDatasetRepository repository,
            Clock clock,
            Supplier<UUID> idGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    public MarketDataset materialize(
            String symbol,
            Timeframe timeframe,
            String datasetVersion,
            List<Candle> candles) {
        return materialize(symbol, timeframe, datasetVersion, candles, List.of());
    }

    public MarketDataset materialize(
            String symbol,
            Timeframe timeframe,
            String datasetVersion,
            List<Candle> candles,
            List<SentimentObservation> sentimentObservations) {
        List<Candle> ordered = List.copyOf(Objects.requireNonNull(candles, "candles must not be null"))
                .stream()
                .sorted(Comparator.comparing(Candle::openTime))
                .toList();
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("dataset candles must not be empty");
        }
        List<SentimentObservation> orderedSentiment = List.copyOf(Objects.requireNonNull(
                        sentimentObservations, "sentimentObservations must not be null"))
                .stream()
                .sorted(Comparator.comparing(SentimentObservation::observedAt)
                        .thenComparing(SentimentObservation::sourceId))
                .toList();
        String checksum = MarketDatasetChecksum.calculate(ordered, orderedSentiment);
        MarketDatasetRef reference = new MarketDatasetRef(
                symbol,
                timeframe,
                ordered.getFirst().openTime(),
                ordered.getLast().openTime().plus(timeframe.duration()),
                datasetVersion,
                checksum);
        return repository.save(
                new MarketDataset(idGenerator.get(), reference, ordered, orderedSentiment),
                clock.instant());
    }
}
