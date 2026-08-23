package com.cryptolab.strategy.port;

import com.cryptolab.strategy.domain.UserStrategyDocument;

public interface StrategyDocumentDecoder {
    UserStrategyDocument decode(String json);
}
