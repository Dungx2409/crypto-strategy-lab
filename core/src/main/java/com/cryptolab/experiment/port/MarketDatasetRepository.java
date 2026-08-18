package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.MarketDataset;
import java.time.Instant;

public interface MarketDatasetRepository {

    MarketDataset save(MarketDataset dataset, Instant createdAt);
}
