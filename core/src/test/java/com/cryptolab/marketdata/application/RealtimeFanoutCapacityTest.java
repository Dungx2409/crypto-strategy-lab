package com.cryptolab.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.CandleUpdatePublisher;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.marketdata.port.MarketDataScheduler;
import com.cryptolab.marketdata.port.MarketDataTelemetry;
import com.cryptolab.marketdata.port.MarketSubscription;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeFanoutCapacityTest {

    @Test
    void oneThousandUsersWithFourChartsShareFourProviderStreams() {
        MarketDataProvider provider = mock(MarketDataProvider.class);
        when(provider.subscribe(any(), any(), any())).thenReturn(mock(MarketSubscription.class));
        MarketDataStreamService service = new MarketDataStreamService(
                provider, mock(CandleStore.class), mock(CandleUpdatePublisher.class),
                mock(MarketDataScheduler.class), mock(MarketDataTelemetry.class), Clock.systemUTC(),
                Duration.ofSeconds(1), Duration.ofSeconds(30));
        Timeframe[] timeframes = {Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4};
        List<MarketStreamRegistration> registrations = new ArrayList<>(4_000);

        long started = System.nanoTime();
        for (int user = 0; user < 1_000; user++) {
            for (Timeframe timeframe : timeframes) {
                registrations.add(service.open(new TradingPair("BTCUSDT"), timeframe));
            }
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(registrations).hasSize(4_000);
        assertThat(service.activeStreamCount()).isEqualTo(4);
        assertThat(elapsedMillis).isLessThan(5_000);
        verify(provider, times(4)).subscribe(any(), any(), any());
        registrations.forEach(MarketStreamRegistration::close);
        assertThat(service.activeStreamCount()).isZero();
        System.out.printf("realtime_capacity users=1000 charts=4000 providerStreams=4 registrationMs=%d%n", elapsedMillis);
    }
}
