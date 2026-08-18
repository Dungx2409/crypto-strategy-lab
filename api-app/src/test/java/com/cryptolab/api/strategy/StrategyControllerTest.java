package com.cryptolab.api.strategy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.infrastructure.strategy.adapter.BollingerBandsStrategyFactory;
import com.cryptolab.infrastructure.strategy.adapter.MovingAverageStrategyFactory;
import com.cryptolab.infrastructure.strategy.adapter.RsiStrategyFactory;
import com.cryptolab.infrastructure.strategy.adapter.SpringStrategyRegistry;
import com.cryptolab.infrastructure.strategy.adapter.SupportResistanceStrategyFactory;
import com.cryptolab.strategy.port.StrategyFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StrategyControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        List<StrategyFactory> factories = List.of(
                new MovingAverageStrategyFactory(),
                new RsiStrategyFactory(),
                new BollingerBandsStrategyFactory(),
                new SupportResistanceStrategyFactory());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new StrategyController(new SpringStrategyRegistry(factories)))
                .build();
    }

    @Test
    void listsFourPluginsWithVersionedParameterSchemasAndDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].type").value("BB"))
                .andExpect(jsonPath("$[0].version").value("1.0"))
                .andExpect(jsonPath("$[0].parameterSchema.window.default").value(20))
                .andExpect(jsonPath("$[1].type").value("MA"))
                .andExpect(jsonPath("$[1].parameterSchema.fastPeriod.default").value(10))
                .andExpect(jsonPath("$[2].type").value("RSI"))
                .andExpect(jsonPath("$[2].parameterSchema.oversold.default").value(30))
                .andExpect(jsonPath("$[3].type").value("SR"));
    }
}
