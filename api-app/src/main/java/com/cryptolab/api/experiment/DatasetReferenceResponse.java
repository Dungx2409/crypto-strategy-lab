package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.MarketDatasetRef;
import java.time.Instant;

public record DatasetReferenceResponse(
        String symbol,
        String timeframe,
        Instant from,
        Instant to,
        String datasetVersion,
        String checksum) {

    static DatasetReferenceResponse from(MarketDatasetRef dataset) {
        return new DatasetReferenceResponse(
                dataset.symbol(),
                dataset.timeframe().exchangeCode(),
                dataset.from(),
                dataset.to(),
                dataset.datasetVersion(),
                dataset.checksum());
    }
}
