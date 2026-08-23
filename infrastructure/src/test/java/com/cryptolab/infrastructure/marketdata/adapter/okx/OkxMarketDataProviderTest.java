package com.cryptolab.infrastructure.marketdata.adapter.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.CandleUpdate;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.MarketSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class OkxMarketDataProviderTest {

    @Test
    void buildsOkxHistoricalRequestAndReturnsTheSharedCandleContract() {
        FakeTransport transport = new FakeTransport();
        transport.payload = """
                {"code":"0","data":[
                  ["1723943100000","105","115","95","110","13","0","0","1"],
                  ["1723942800000","100","110","90","105","12","0","0","1"]
                ]}
                """;
        OkxMarketDataProvider provider = provider(transport);

        var candles = provider.loadHistorical(
                new TradingPair("BTCUSDT"),
                Timeframe.M5,
                Instant.ofEpochMilli(1723942800000L),
                Instant.ofEpochMilli(1723943400000L));

        assertThat(candles).hasSize(2);
        assertThat(candles.getFirst().symbol()).isEqualTo("BTCUSDT");
        assertThat(transport.lastUri.getQuery())
                .contains("instId=BTC-USDT", "bar=5m", "after=1723943400000", "limit=300");
    }

    @Test
    void translatesRealtimeProtocolWithoutChangingTheListenerContract() {
        FakeTransport transport = new FakeTransport();
        OkxMarketDataProvider provider = provider(transport);
        List<CandleUpdate> received = new ArrayList<>();

        provider.subscribe(new TradingPair("ETHUSDT"), Timeframe.H1, received::add);
        transport.message.accept("""
                {"arg":{"channel":"candle1H","instId":"ETH-USDT"},"data":[
                  ["1723939200000","100","110","90","105","12","0","0","1"]
                ]}
                """);

        assertThat(transport.lastUri).isEqualTo(URI.create("wss://example.test/ws/v5/public"));
        assertThat(transport.subscriptionMessage)
                .isEqualTo("{\"op\":\"subscribe\",\"args\":[{\"channel\":\"candle1H\",\"instId\":\"ETH-USDT\"}]}");
        assertThat(received).singleElement().satisfies(update -> {
            assertThat(update.candle().symbol()).isEqualTo("ETHUSDT");
            assertThat(update.candle().timeframe()).isEqualTo(Timeframe.H1);
            assertThat(update.closed()).isTrue();
        });
        assertThat(OkxCandleDto.class.getModifiers() & java.lang.reflect.Modifier.PUBLIC).isZero();
    }

    @Test
    void usesOkxSpecificCodesWithoutChangingTheSharedTimeframeEnum() {
        assertThat(OkxMarketDataProvider.interval(Timeframe.M30)).isEqualTo("30m");
        assertThat(OkxMarketDataProvider.interval(Timeframe.H2)).isEqualTo("2H");
        assertThat(OkxMarketDataProvider.interval(Timeframe.D1)).isEqualTo("1D");
    }

    private OkxMarketDataProvider provider(FakeTransport transport) {
        return new OkxMarketDataProvider(
                URI.create("https://example.test/api/v5/market/history-candles"),
                URI.create("wss://example.test/ws/v5/public"),
                transport,
                new OkxPayloadMapper(new ObjectMapper()));
    }

    private static final class FakeTransport implements OkxTransport {
        private URI lastUri;
        private String payload = "{\"code\":\"0\",\"data\":[]}";
        private String subscriptionMessage;
        private Consumer<String> message;

        @Override
        public String get(URI uri) {
            lastUri = uri;
            return payload;
        }

        @Override
        public MarketSubscription connect(
                URI uri,
                String subscriptionMessage,
                Runnable onConnected,
                Consumer<String> onMessage,
                Consumer<Throwable> onDisconnected) {
            lastUri = uri;
            this.subscriptionMessage = subscriptionMessage;
            message = onMessage;
            onConnected.run();
            return new MarketSubscription() {
                @Override
                public boolean isActive() {
                    return true;
                }

                @Override
                public void close() {}
            };
        }
    }
}
