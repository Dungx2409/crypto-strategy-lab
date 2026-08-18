package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetRef;

public interface MarketDatasetProvider {
    MarketDataset getDataset(MarketDatasetRef reference);
}
