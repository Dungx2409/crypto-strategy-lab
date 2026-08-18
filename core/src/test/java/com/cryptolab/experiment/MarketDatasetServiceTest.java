package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.MarketDatasetService;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MarketDatasetServiceTest {

    @Test
    void sortsBackendCandlesAndCreatesAnExactChecksummedReference() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        UUID id = UUID.fromString("90000000-0000-0000-0000-000000000001");
        AtomicReference<MarketDataset> saved = new AtomicReference<>();
        MarketDatasetService service = new MarketDatasetService(
                (dataset, createdAt) -> {
                    assertThat(createdAt).isEqualTo(now);
                    saved.set(dataset);
                    return dataset;
                },
                Clock.fixed(now, ZoneOffset.UTC),
                () -> id);

        MarketDataset result = service.materialize(
                "btcusdt", Timeframe.M5, "dashboard-v1", List.of(candle(1), candle(0)));

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.reference().symbol()).isEqualTo("BTCUSDT");
        assertThat(result.reference().from()).isEqualTo(candle(0).openTime());
        assertThat(result.reference().to()).isEqualTo(candle(1).openTime().plus(Timeframe.M5.duration()));
        assertThat(result.reference().checksum()).hasSize(64);
        assertThat(saved.get().candles()).extracting(Candle::openTime).isSorted();
    }

    private static Candle candle(int index) {
        return new Candle(
                "BTCUSDT",
                Timeframe.M5,
                Instant.parse("2026-08-18T00:00:00Z").plusSeconds(index * 300L),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("90"),
                new BigDecimal("105"),
                BigDecimal.TEN);
    }
}
