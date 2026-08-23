package com.cryptolab.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainContractTest {

    @Test
    void normalizesTradingPairAndMapsSupportedTimeframe() {
        assertThat(new TradingPair(" btcusdt ").symbol()).isEqualTo("BTCUSDT");
        assertThat(Timeframe.values())
                .extracting(Timeframe::exchangeCode)
                .containsExactly("1m", "5m", "15m", "30m", "1h", "2h", "4h", "1d");
        assertThat(Timeframe.fromExchangeCode("1m")).isEqualTo(Timeframe.M1);
        assertThat(Timeframe.fromExchangeCode("30m")).isEqualTo(Timeframe.M30);
        assertThat(Timeframe.fromExchangeCode("2h")).isEqualTo(Timeframe.H2);
        assertThat(Timeframe.fromExchangeCode("1d")).isEqualTo(Timeframe.D1);
        assertThatThrownBy(() -> Timeframe.fromExchangeCode("2m"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported timeframe: 2m");
    }

    @Test
    void rejectsInvalidFinancialDomainValues() {
        Instant at = Instant.parse("2026-08-18T01:00:00Z");

        assertThatThrownBy(() -> new Candle(
                        "BTCUSDT",
                        Timeframe.M5,
                        at,
                        new BigDecimal("100"),
                        new BigDecimal("99"),
                        new BigDecimal("98"),
                        new BigDecimal("99"),
                        BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("high");

        assertThatThrownBy(() -> new Signal(
                        SignalType.BUY, new BigDecimal("1.01"), at, "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("strength must be in [-1, 1]");
    }

    @Test
    void requiresAtLeastOneBoundedAutomaticStopCondition() {
        assertThatThrownBy(() -> new StopConditions(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one automatic stop condition is required");

        assertThat(new StopConditions(125L, null, null).maxCandidates()).isEqualTo(125L);
    }
}
