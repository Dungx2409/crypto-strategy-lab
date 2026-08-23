package com.cryptolab.marketdata.domain;

import java.time.Duration;
import java.util.Arrays;

public enum Timeframe {
    M1("1m", Duration.ofMinutes(1)),
    M5("5m", Duration.ofMinutes(5)),
    M15("15m", Duration.ofMinutes(15)),
    M30("30m", Duration.ofMinutes(30)),
    H1("1h", Duration.ofHours(1)),
    H2("2h", Duration.ofHours(2)),
    H4("4h", Duration.ofHours(4)),
    D1("1d", Duration.ofDays(1));

    private final String exchangeCode;
    private final Duration duration;

    Timeframe(String exchangeCode, Duration duration) {
        this.exchangeCode = exchangeCode;
        this.duration = duration;
    }

    public String exchangeCode() {
        return exchangeCode;
    }

    public Duration duration() {
        return duration;
    }

    public static Timeframe fromExchangeCode(String exchangeCode) {
        return Arrays.stream(values())
                .filter(timeframe -> timeframe.exchangeCode.equals(exchangeCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe: " + exchangeCode));
    }
}
