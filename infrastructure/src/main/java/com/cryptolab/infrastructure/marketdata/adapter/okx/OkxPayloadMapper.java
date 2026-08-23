package com.cryptolab.infrastructure.marketdata.adapter.okx;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.CandleUpdate;
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

final class OkxPayloadMapper {

    private final ObjectMapper objectMapper;

    OkxPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Candle> historical(String payload, TradingPair pair, Timeframe timeframe) {
        JsonNode root = response(payload);
        List<Candle> candles = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            candles.add(toCandle(dto(item), pair, timeframe));
        }
        return List.copyOf(candles);
    }

    Optional<CandleUpdate> realtime(
            String payload, TradingPair expectedPair, Timeframe expectedTimeframe) {
        JsonNode root = response(payload);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return Optional.empty();
        }
        String instrumentId = root.path("arg").path("instId").asText();
        if (!instrumentId.equals(OkxMarketDataProvider.instrumentId(expectedPair))) {
            return Optional.empty();
        }
        JsonNode item = data.isEmpty() ? null : data.get(0);
        if (item == null) {
            return Optional.empty();
        }
        OkxCandleDto dto = dto(item);
        return Optional.of(new CandleUpdate(toCandle(dto, expectedPair, expectedTimeframe), dto.closed()));
    }

    private JsonNode response(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String code = root.path("code").asText("0");
            if (!code.equals("0")) {
                throw new IllegalArgumentException("OKX returned error " + code + ": " + root.path("msg").asText());
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot parse OKX response", exception);
        }
    }

    private OkxCandleDto dto(JsonNode item) {
        if (!item.isArray() || item.size() < 9) {
            throw new IllegalArgumentException("OKX candle is malformed");
        }
        return new OkxCandleDto(
                item.get(0).asLong(),
                item.get(1).asText(),
                item.get(2).asText(),
                item.get(3).asText(),
                item.get(4).asText(),
                item.get(5).asText(),
                "1".equals(item.get(8).asText()));
    }

    private Candle toCandle(OkxCandleDto dto, TradingPair pair, Timeframe timeframe) {
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
}
