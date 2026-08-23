package com.cryptolab.api.marketdata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.marketdata.application.MarketDataService;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleListener;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.marketdata.port.MarketSubscription;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class MarketDataControllerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-18T02:00:00Z"), ZoneOffset.UTC);

    private StubProvider provider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        provider = new StubProvider();
        provider.candles = List.of(candle());
        MarketDataService service =
                new MarketDataService(provider, new InMemoryStore(), CLOCK, Set.of("BTCUSDT"), 500);
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataController(service))
                .setControllerAdvice(new MarketDataExceptionHandler(CLOCK))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper()
                                .findAndRegisterModules()
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    void returnsStableMarketContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("timeframe", "5m")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.timeframe").value("5m"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.candles[0].openTime").value("2026-08-18T01:50:00Z"))
                .andExpect(jsonPath("$.candles[0].close").value("105.40"));
    }

    @Test
    void mapsValidationAndProviderFailuresToRequiredErrors() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("timeframe", "2m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEFRAME"))
                .andExpect(jsonPath("$.message").value("Unsupported timeframe: 2m"));

        provider.failure = new IllegalStateException("Binance unavailable");
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("timeframe", "5m"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_UNAVAILABLE"));
    }

    @Test
    void acceptsAnExplicitHistoricalRangeAndRejectsHalfARange() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("timeframe", "5m")
                        .param("from", "2026-08-18T01:00:00Z")
                        .param("to", "2026-08-18T02:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candles[0].openTime").value("2026-08-18T01:50:00Z"));

        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("timeframe", "5m")
                        .param("from", "2026-08-18T01:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    private static Candle candle() {
        return new Candle(
                "BTCUSDT",
                Timeframe.M5,
                Instant.parse("2026-08-18T01:50:00Z"),
                new BigDecimal("100.10"),
                new BigDecimal("110.20"),
                new BigDecimal("90.30"),
                new BigDecimal("105.40"),
                new BigDecimal("12.50"));
    }

    private static final class StubProvider implements MarketDataProvider {
        private List<Candle> candles = List.of();
        private RuntimeException failure;

        @Override
        public List<Candle> loadHistorical(
                TradingPair pair, Timeframe timeframe, Instant from, Instant to) {
            if (failure != null) throw failure;
            return candles;
        }

        @Override
        public MarketSubscription subscribe(
                TradingPair pair, Timeframe timeframe, CandleListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryStore implements CandleStore {
        private final List<Candle> candles = new ArrayList<>();

        @Override
        public boolean saveIfAbsent(Candle candle) {
            if (candles.stream().anyMatch(existing -> existing.openTime().equals(candle.openTime()))) {
                return false;
            }
            candles.add(candle);
            return true;
        }

        @Override
        public List<Candle> findLatest(TradingPair pair, Timeframe timeframe, int limit) {
            return candles.stream()
                    .sorted(Comparator.comparing(Candle::openTime))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Instant> findLastOpenTime(TradingPair pair, Timeframe timeframe) {
            return candles.stream().map(Candle::openTime).max(Comparator.naturalOrder());
        }
    }
}
