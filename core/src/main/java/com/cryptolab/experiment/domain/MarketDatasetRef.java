package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Timeframe;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record MarketDatasetRef(
        String symbol,
        Timeframe timeframe,
        Instant from,
        Instant to,
        String datasetVersion,
        String checksum) {

    public MarketDatasetRef {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        symbol = symbol.trim().toUpperCase(Locale.ROOT);
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("dataset from must be before to");
        }
        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw new IllegalArgumentException("datasetVersion must not be blank");
        }
        datasetVersion = datasetVersion.trim();
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("checksum must not be blank");
        }
        checksum = checksum.trim();
    }
}
