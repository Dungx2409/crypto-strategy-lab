package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.marketdata.domain.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentCandlesResponse(
        UUID datasetId,
        String checksum,
        List<Item> candles) {

    static ExperimentCandlesResponse from(MarketDataset dataset) {
        return new ExperimentCandlesResponse(
                dataset.id(),
                dataset.reference().checksum(),
                dataset.candles().stream().map(Item::from).toList());
    }

    public record Item(
            Instant openTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume) {

        private static Item from(Candle candle) {
            return new Item(
                    candle.openTime(), candle.open(), candle.high(), candle.low(),
                    candle.close(), candle.volume());
        }
    }
}
