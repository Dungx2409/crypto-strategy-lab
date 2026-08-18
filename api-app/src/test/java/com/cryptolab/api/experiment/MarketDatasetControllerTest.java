package com.cryptolab.api.experiment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.experiment.application.MarketDatasetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MarketDatasetControllerTest {

    @Test
    void materializesBackendCandlesIntoAnExactDatasetReference() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC);
        MarketDatasetService service = new MarketDatasetService(
                (dataset, createdAt) -> dataset,
                clock,
                () -> UUID.fromString("90000000-0000-0000-0000-000000000002"));
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var mockMvc = MockMvcBuilders.standaloneSetup(new MarketDatasetController(service))
                .setControllerAdvice(new ExperimentExceptionHandler(clock))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();

        mockMvc.perform(post("/api/v1/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol":"BTCUSDT","timeframe":"5m","datasetVersion":"dashboard-v1",
                                  "candles":[
                                    {"openTime":"2026-08-18T00:00:00Z","open":100,"high":110,"low":90,"close":105,"volume":10},
                                    {"openTime":"2026-08-18T00:05:00Z","open":105,"high":115,"low":95,"close":110,"volume":12}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.timeframe").value("5m"))
                .andExpect(jsonPath("$.datasetVersion").value("dashboard-v1"))
                .andExpect(jsonPath("$.checksum").isNotEmpty());
    }
}
