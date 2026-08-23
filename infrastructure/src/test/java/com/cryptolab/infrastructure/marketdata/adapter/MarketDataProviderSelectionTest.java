package com.cryptolab.infrastructure.marketdata.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.infrastructure.marketdata.adapter.binance.BinanceMarketDataProvider;
import com.cryptolab.infrastructure.marketdata.adapter.okx.OkxMarketDataProvider;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MarketDataProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(BinanceMarketDataProvider.class, OkxMarketDataProvider.class);

    @Test
    void selectsBinanceByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MarketDataProvider.class);
            assertThat(context).hasSingleBean(BinanceMarketDataProvider.class);
            assertThat(context).doesNotHaveBean(OkxMarketDataProvider.class);
        });
    }

    @Test
    void replacesBinanceWithOkxFromConfiguration() {
        contextRunner.withPropertyValues("crypto.market.provider=okx").run(context -> {
            assertThat(context).hasSingleBean(MarketDataProvider.class);
            assertThat(context).hasSingleBean(OkxMarketDataProvider.class);
            assertThat(context).doesNotHaveBean(BinanceMarketDataProvider.class);
        });
    }
}
