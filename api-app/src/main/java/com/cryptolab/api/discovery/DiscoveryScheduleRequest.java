package com.cryptolab.api.discovery;

import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Duration;

public record DiscoveryScheduleRequest(
        String symbol,
        String timeframe,
        Duration lookback,
        BigDecimal initialCapital,
        Long candidateLimit,
        Duration interval) {

    Timeframe parsedTimeframe() {
        return Timeframe.fromExchangeCode(timeframe);
    }

    Duration resolvedLookback() {
        return lookback == null ? Duration.ofDays(365) : lookback;
    }

    BigDecimal resolvedCapital() {
        return initialCapital == null ? new BigDecimal("10000") : initialCapital;
    }

    long resolvedCandidateLimit() {
        return candidateLimit == null ? 125 : candidateLimit;
    }

    Duration resolvedInterval() {
        return interval == null ? Duration.ofHours(24) : interval;
    }
}
