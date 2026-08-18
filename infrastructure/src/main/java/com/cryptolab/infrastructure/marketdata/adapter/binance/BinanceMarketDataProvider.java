package com.cryptolab.infrastructure.marketdata.adapter.binance;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class BinanceMarketDataProvider implements MarketDataProvider {

    private static final int PAGE_SIZE = 1000;

    private final URI restEndpoint;
    private final String websocketBaseUrl;
    private final BinanceTransport transport;
    private final BinancePayloadMapper mapper;

    @Autowired
    public BinanceMarketDataProvider(
            ObjectMapper objectMapper,
            @Value("${crypto.market.binance.rest-url:https://api.binance.com/api/v3/klines}")
                    String restUrl,
            @Value("${crypto.market.binance.websocket-url:wss://stream.binance.com:9443/ws}")
                    String websocketUrl,
            @Value("${crypto.market.binance.connect-timeout:5s}") Duration connectTimeout,
            @Value("${crypto.market.binance.request-timeout:10s}") Duration requestTimeout) {
        this(
                URI.create(restUrl),
                websocketUrl,
                new JdkBinanceTransport(connectTimeout, requestTimeout),
                new BinancePayloadMapper(objectMapper));
    }

    BinanceMarketDataProvider(
            URI restEndpoint,
            String websocketBaseUrl,
            BinanceTransport transport,
            BinancePayloadMapper mapper) {
        this.restEndpoint = restEndpoint;
        this.websocketBaseUrl = stripTrailingSlash(websocketBaseUrl);
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
        Instant cursor = from;
        while (cursor.isBefore(to)) {
            URI uri = historicalUri(pair, timeframe, cursor, to);
            List<Candle> page = mapper.historical(transport.get(uri), pair, timeframe).stream()
                    .filter(candle -> !candle.openTime().isBefore(from))
                    .filter(candle -> candle.openTime().plus(timeframe.duration()).compareTo(to) <= 0)
                    .sorted(Comparator.comparing(Candle::openTime))
                    .toList();
            page.forEach(candle -> unique.putIfAbsent(candle.openTime(), candle));
            if (page.size() < PAGE_SIZE) {
                break;
            }
            Instant next = page.getLast().openTime().plus(timeframe.duration());
            if (!next.isAfter(cursor)) {
                break;
            }
            cursor = next;
        }
        return unique.values().stream().sorted(Comparator.comparing(Candle::openTime)).toList();
    }

    @Override
    public MarketSubscription subscribe(
            TradingPair pair, Timeframe timeframe, CandleListener listener) {
        URI uri = URI.create(websocketBaseUrl
                + "/"
                + pair.symbol().toLowerCase(Locale.ROOT)
                + "@kline_"
                + timeframe.exchangeCode());
        return transport.connect(
                uri,
                listener::onConnected,
                payload -> mapper.realtime(payload).ifPresent(candle -> {
                    if (candle.symbol().equals(pair.symbol()) && candle.timeframe() == timeframe) {
                        listener.onCandle(candle);
                    }
                }),
                listener::onDisconnected);
    }

    private URI historicalUri(
            TradingPair pair, Timeframe timeframe, Instant from, Instant to) {
        String separator = restEndpoint.toString().contains("?") ? "&" : "?";
        String query = "symbol="
                + encode(pair.symbol())
                + "&interval="
                + encode(timeframe.exchangeCode())
                + "&startTime="
                + from.toEpochMilli()
                + "&endTime="
                + to.toEpochMilli()
                + "&limit="
                + PAGE_SIZE;
        return URI.create(restEndpoint + separator + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
