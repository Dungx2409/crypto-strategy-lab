package com.cryptolab.api.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.application.MarketDataStreamService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MarketSubscriptionTrackerWiringTest {

    @Test
    void lazilyResolvesStreamServiceAfterTheStompBrokerHasStarted() {
        var constructor = MarketSubscriptionTracker.class.getDeclaredConstructors()[0];

        assertThat(Arrays.asList(constructor.getParameterTypes()))
                .contains(ObjectProvider.class)
                .doesNotContain(MarketDataStreamService.class);
    }
}
