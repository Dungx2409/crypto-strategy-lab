package com.cryptolab.infrastructure.marketdata.adapter.binance;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class BinancePayloadMapper {

    private final ObjectMapper objectMapper;

    BinancePayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Candle> historical(String payload, TradingPair pair, Timeframe timeframe) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.isArray()) {
                throw new IllegalArgumentException("Binance historical response must be an array");
            }
            List<Candle> candles = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isArray() || item.size() < 6) {
                    throw new IllegalArgumentException("Binance historical kline is malformed");
                }
                BinanceKlineDto dto = new BinanceKlineDto(
                        item.get(0).asLong(),
                        item.get(1).asText(),
                        item.get(2).asText(),
                        item.get(3).asText(),
                        item.get(4).asText(),
                        item.get(5).asText(),
                        true);
                candles.add(toCandle(dto, pair, timeframe));
            }
            return List.copyOf(candles);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot parse Binance historical response", exception);
        }
    }

    Optional<Candle> realtime(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode kline = root.path("k");
            if (kline.isMissingNode()) {
                throw new IllegalArgumentException("Binance realtime event has no kline payload");
            }
            BinanceKlineDto dto = new BinanceKlineDto(
                    kline.path("t").asLong(),
                    requiredText(kline, "o"),
                    requiredText(kline, "h"),
                    requiredText(kline, "l"),
                    requiredText(kline, "c"),
                    requiredText(kline, "v"),
                    kline.path("x").asBoolean(false));
            if (!dto.closed()) {
                return Optional.empty();
            }
            TradingPair pair = new TradingPair(requiredText(root, "s"));
            Timeframe timeframe = Timeframe.fromExchangeCode(requiredText(kline, "i"));
            return Optional.of(toCandle(dto, pair, timeframe));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot parse Binance realtime event", exception);
        }
    }

    private Candle toCandle(BinanceKlineDto dto, TradingPair pair, Timeframe timeframe) {
        return new Candle(
                pair.symbol(),
                timeframe,
                Instant.ofEpochMilli(dto.openTime()),
                new BigDecimal(dto.open()),
                new BigDecimal(dto.high()),
                new BigDecimal(dto.low()),
                new BigDecimal(dto.close()),
                new BigDecimal(dto.volume()));
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing Binance field: " + field);
        }
        return value.asText();
    }
}
