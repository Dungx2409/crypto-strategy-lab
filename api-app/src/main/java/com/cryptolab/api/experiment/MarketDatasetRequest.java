package com.cryptolab.api.experiment;

import com.cryptolab.experiment.application.MarketDatasetService;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.marketdata.domain.Timeframe;
import java.util.List;

public record MarketDatasetRequest(
        String symbol,
        String timeframe,
        String datasetVersion,
        List<ExperimentCandleRequest> candles,
        List<SentimentObservationRequest> sentimentObservations) {

    MarketDataset materialize(MarketDatasetService service) {
        Timeframe parsed = Timeframe.fromExchangeCode(timeframe);
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("candles must not be empty");
        }
        return service.materialize(
                symbol,
                parsed,
                datasetVersion,
                candles.stream().map(candle -> candle.toDomain(symbol, parsed)).toList(),
                sentimentObservations == null
                        ? List.of()
                        : sentimentObservations.stream()
                                .map(SentimentObservationRequest::toDomain)
                                .toList());
    }
}
