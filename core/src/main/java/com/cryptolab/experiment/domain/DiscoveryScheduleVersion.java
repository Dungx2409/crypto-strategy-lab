package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record DiscoveryScheduleVersion(
        UUID scheduleId,
        int version,
        String symbol,
        Timeframe timeframe,
        Duration lookback,
        BigDecimal initialCapital,
        long candidateLimit,
        Duration interval,
        Instant createdAt) {}
