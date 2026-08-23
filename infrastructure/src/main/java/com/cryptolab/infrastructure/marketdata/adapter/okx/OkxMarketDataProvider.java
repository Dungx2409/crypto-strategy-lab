package com.cryptolab.infrastructure.marketdata.adapter.okx;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleListener;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.marketdata.port.MarketSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "crypto.market.provider", havingValue = "okx")
public final class OkxMarketDataProvider implements MarketDataProvider {

    private static final int PAGE_SIZE = 300;

    private final URI restEndpoint;
    private final URI websocketEndpoint;
    private final OkxTransport transport;
    private final OkxPayloadMapper mapper;

    @Autowired
    public OkxMarketDataProvider(
            ObjectMapper objectMapper,
            @Value("${crypto.market.okx.rest-url:https://www.okx.com/api/v5/market/history-candles}")
                    String restUrl,
            @Value("${crypto.market.okx.websocket-url:wss://ws.okx.com:8443/ws/v5/public}")
                    String websocketUrl,
            @Value("${crypto.market.okx.connect-timeout:5s}") Duration connectTimeout,
            @Value("${crypto.market.okx.request-timeout:10s}") Duration requestTimeout) {
        this(
                URI.create(restUrl),
                URI.create(websocketUrl),
                new JdkOkxTransport(connectTimeout, requestTimeout),
                new OkxPayloadMapper(objectMapper));
    }

    OkxMarketDataProvider(
            URI restEndpoint,
            URI websocketEndpoint,
            OkxTransport transport,
            OkxPayloadMapper mapper) {
        this.restEndpoint = restEndpoint;
        this.websocketEndpoint = websocketEndpoint;
        this.transport = transport;
        this.mapper = mapper;
    }

    @Override
    public List<Candle> loadHistorical(
            TradingPair pair, Timeframe timeframe, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            return List.of();
        }
        Map<Instant, Candle> unique = new LinkedHashMap<>();
        Instant cursor = to;
        while (cursor.isAfter(from)) {
            List<Candle> rawPage = mapper.historical(
                    transport.get(historicalUri(pair, timeframe, cursor)), pair, timeframe);
            List<Candle> page = rawPage.stream()
                    .filter(candle -> !candle.openTime().isBefore(from))
                    .filter(candle -> candle.openTime().plus(timeframe.duration()).compareTo(to) <= 0)
                    .sorted(Comparator.comparing(Candle::openTime))
                    .toList();
            page.forEach(candle -> unique.putIfAbsent(candle.openTime(), candle));
            if (rawPage.size() < PAGE_SIZE) {
                break;
            }
            Instant next = rawPage.stream()
                    .map(Candle::openTime)
                    .min(Instant::compareTo)
                    .orElse(cursor);
            if (!next.isBefore(cursor)) {
                break;
            }
            cursor = next;
        }
        return unique.values().stream().sorted(Comparator.comparing(Candle::openTime)).toList();
    }

    @Override
    public MarketSubscription subscribe(
            TradingPair pair, Timeframe timeframe, CandleListener listener) {
        return transport.connect(
                websocketEndpoint,
                subscriptionMessage(pair, timeframe),
                listener::onConnected,
                payload -> mapper.realtime(payload, pair, timeframe).ifPresent(listener::onCandle),
                listener::onDisconnected);
    }

    private URI historicalUri(TradingPair pair, Timeframe timeframe, Instant before) {
        String separator = restEndpoint.toString().contains("?") ? "&" : "?";
        String query = "instId="
                + encode(instrumentId(pair))
                + "&bar="
                + encode(interval(timeframe))
                + "&after="
                + before.toEpochMilli()
                + "&limit="
                + PAGE_SIZE;
        return URI.create(restEndpoint + separator + query);
    }

    private String subscriptionMessage(TradingPair pair, Timeframe timeframe) {
        return """
                {"op":"subscribe","args":[{"channel":"candle%s","instId":"%s"}]}
                """.formatted(interval(timeframe), instrumentId(pair)).strip();
    }

    static String instrumentId(TradingPair pair) {
        String symbol = pair.symbol();
        if (!symbol.endsWith("USDT") || symbol.length() == 4) {
            throw new IllegalArgumentException("OKX adapter supports USDT pairs such as BTCUSDT");
        }
        return symbol.substring(0, symbol.length() - 4) + "-USDT";
    }

    static String interval(Timeframe timeframe) {
        return switch (timeframe) {
            case M1 -> "1m";
            case M5 -> "5m";
            case M15 -> "15m";
            case M30 -> "30m";
            case H1 -> "1H";
            case H2 -> "2H";
            case H4 -> "4H";
            case D1 -> "1D";
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
