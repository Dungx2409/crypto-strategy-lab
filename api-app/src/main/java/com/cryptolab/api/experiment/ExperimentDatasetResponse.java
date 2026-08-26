package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.marketdata.domain.Candle;
import java.util.List;

public record ExperimentDatasetResponse(
        DatasetReferenceResponse reference, List<Candle> candles) {

    static ExperimentDatasetResponse from(MarketDataset dataset) {
        return new ExperimentDatasetResponse(
                DatasetReferenceResponse.from(dataset.reference()), dataset.candles());
    }
}
