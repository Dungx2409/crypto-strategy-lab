package com.cryptolab.api.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.infrastructure.news.adapter.DeterministicKeywordSentimentAnalyzer;
import com.cryptolab.marketdata.application.MarketDataService;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.marketdata.port.CandleListener;
import com.cryptolab.marketdata.port.CandleStore;
import com.cryptolab.marketdata.port.MarketDataProvider;
import com.cryptolab.marketdata.port.MarketSubscription;
import com.cryptolab.news.application.NewsCollector;
import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsHealthStatus;
import com.cryptolab.news.domain.NewsInsight;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.NewsStore;
import com.cryptolab.news.port.NewsTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NewsFailureIsolationTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void throwingNewsAdapterDegradesOnlyNewsWhileMarketApiStillPasses() throws Exception {
        NewsCollector news = new NewsCollector(
                since -> {
                    throw new IllegalStateException("news provider down");
                },
                new DeterministicKeywordSentimentAnalyzer(CLOCK),
                new EmptyNewsStore(),
                NewsTelemetry.noop(),
                CLOCK,
                Duration.ofHours(24),
                2);

        assertThat(news.collect().providerStatus()).isEqualTo(NewsHealthStatus.DOWN);

        MarketDataService market = new MarketDataService(
                new AvailableMarketProvider(),
                new InMemoryCandleStore(),
                CLOCK,
                Set.of("BTCUSDT"),
                500);
        var mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataController(market))
                .setControllerAdvice(new MarketDataExceptionHandler(CLOCK))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper()
                                .findAndRegisterModules()
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();

        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("timeframe", "5m")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.candles[0].close").value("105"));
    }

    private static Candle candle() {
        return new Candle(
                "BTCUSDT", Timeframe.M5, NOW.minusSeconds(300),
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"),
                new BigDecimal("105"), new BigDecimal("12"));
    }

    private static final class AvailableMarketProvider implements MarketDataProvider {
        @Override
        public List<Candle> loadHistorical(
                TradingPair pair, Timeframe timeframe, Instant from, Instant to) {
            return List.of(candle());
        }

        @Override
        public MarketSubscription subscribe(
                TradingPair pair, Timeframe timeframe, CandleListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryCandleStore implements CandleStore {
        private final List<Candle> candles = new ArrayList<>();

        @Override
        public boolean saveIfAbsent(Candle candle) {
            candles.add(candle);
            return true;
        }

        @Override
        public List<Candle> findLatest(TradingPair pair, Timeframe timeframe, int limit) {
            return candles.stream().sorted(Comparator.comparing(Candle::openTime)).limit(limit).toList();
        }

        @Override
        public Optional<Instant> findLastOpenTime(TradingPair pair, Timeframe timeframe) {
            return candles.stream().map(Candle::openTime).max(Comparator.naturalOrder());
        }
    }

    private static final class EmptyNewsStore implements NewsStore {
        @Override
        public int saveNewsItems(List<NewsItem> items, Instant storedAt) {
            return items.size();
        }

        @Override
        public boolean hasPrediction(
                String newsId,
                String inputVersion,
                ModelDescriptor model,
                String preprocessingVersion) {
            return false;
        }

        @Override
        public void saveSentiment(SentimentResult result) {}

        @Override
        public List<NewsInsight> findLatest(int limit) {
            return List.of();
        }

        @Override
        public Optional<Instant> latestPublishedAt() {
            return Optional.empty();
        }
    }
}
