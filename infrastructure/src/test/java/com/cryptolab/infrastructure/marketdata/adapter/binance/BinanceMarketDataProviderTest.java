package com.cryptolab.infrastructure.marketdata.adapter.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleListener;
import com.cryptolab.marketdata.port.MarketSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BinanceMarketDataProviderTest {

    @Test
    void marksTheProductionConstructorForDeterministicSpringWiring() {
        long autowiredConstructors = java.util.Arrays.stream(BinanceMarketDataProvider.class.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertThat(autowiredConstructors).isEqualTo(1);
    }

    @Test
    void buildsBinanceRequestAndReturnsOnlyCompletedCandles() {
        FakeTransport transport = new FakeTransport();
        transport.payload = """
                [
                  [1723942800000,"100","110","90","105","12",1723943099999],
                  [1723943100000,"105","115","95","110","13",1723943399999]
                ]
                """;
        BinanceMarketDataProvider provider = provider(transport);
        Instant from = Instant.ofEpochMilli(1723942800000L);
        Instant to = Instant.ofEpochMilli(1723943400000L);

        List<Candle> candles =
                provider.loadHistorical(new TradingPair("BTCUSDT"), Timeframe.M5, from, to);

        assertThat(candles).hasSize(2);
        assertThat(transport.lastUri.getQuery())
                .contains("symbol=BTCUSDT", "interval=5m", "limit=1000");
    }

    @Test
    void containsWebsocketProtocolAndDtoTranslationInsideAdapter() {
        FakeTransport transport = new FakeTransport();
        BinanceMarketDataProvider provider = provider(transport);
        List<Candle> received = new ArrayList<>();
        CandleListener listener = received::add;

        provider.subscribe(new TradingPair("BTCUSDT"), Timeframe.H1, listener);
        transport.message.accept("""
                {"s":"BTCUSDT","k":{"t":1723939200000,"i":"1h","o":"100","h":"110","l":"90","c":"105","v":"12","x":true}}
                """);

        assertThat(transport.lastUri.toString()).endsWith("/btcusdt@kline_1h");
        assertThat(received).singleElement().extracting(Candle::timeframe).isEqualTo(Timeframe.H1);
        assertThat(BinanceKlineDto.class.getModifiers() & java.lang.reflect.Modifier.PUBLIC).isZero();
    }

    private BinanceMarketDataProvider provider(FakeTransport transport) {
        return new BinanceMarketDataProvider(
                URI.create("https://example.test/api/v3/klines"),
                "wss://example.test/ws/",
                transport,
                new BinancePayloadMapper(new ObjectMapper()));
    }

    private static final class FakeTransport implements BinanceTransport {
        private URI lastUri;
        private String payload = "[]";
        private Consumer<String> message;

        @Override
        public String get(URI uri) {
            lastUri = uri;
            return payload;
        }

        @Override
        public MarketSubscription connect(
                URI uri,
                Runnable onConnected,
                Consumer<String> onMessage,
                Consumer<Throwable> onDisconnected) {
            lastUri = uri;
            message = onMessage;
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
