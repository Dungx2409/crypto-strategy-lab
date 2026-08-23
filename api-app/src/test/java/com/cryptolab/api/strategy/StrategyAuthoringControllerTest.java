package com.cryptolab.api.strategy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.strategy.application.StrategyAuthoringService;
import com.cryptolab.strategy.port.StrategyAuthoringModel;
import com.cryptolab.strategy.port.StrategyDocumentDecoder;
import com.cryptolab.strategy.port.StrategyRegistry;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StrategyAuthoringControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private StrategyAuthoringModel model;
    private UserStrategyRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        model = mock(StrategyAuthoringModel.class);
        repository = mock(UserStrategyRepository.class);
        StrategyRegistry registry = mock(StrategyRegistry.class);
        when(registry.availableStrategies()).thenReturn(java.util.List.of());
        when(model.proposeIdea(anyString(), anyList())).thenReturn("Use RSI with MA confirmation");
        when(repository.createDraft(any())).thenAnswer(invocation -> invocation.getArgument(0));
        StrategyAuthoringService service = new StrategyAuthoringService(
                model,
                mock(StrategyDocumentDecoder.class),
                repository,
                registry,
                mock(CombinationPolicyResolver.class),
                clock,
                () -> UUID.fromString("00000000-0000-0000-0000-000000000043"));
        mockMvc = MockMvcBuilders.standaloneSetup(new StrategyAuthoringController(service))
                .setControllerAdvice(new StrategyAuthoringExceptionHandler(clock))
                .build();
    }

    @Test
    void sendsOnePromptSourceUnderTheSessionAccount() throws Exception {
        mockMvc.perform(post("/api/v1/user-strategies/drafts")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Combine RSI and MA\"}"))
                .andExpect(status().isCreated());

        verify(model).proposeIdea(eq("Combine RSI and MA"), anyList());
    }

    @Test
    void rejectsAmbiguousSourcesAndMissingSessions() throws Exception {
        mockMvc.perform(post("/api/v1/user-strategies/drafts")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"RSI\",\"articleUrl\":\"https://example.com/a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STRATEGY_REQUEST"));
        verifyNoInteractions(model, repository);

        mockMvc.perform(post("/api/v1/user-strategies/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"RSI\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private static MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedAccount.class.getName(),
                new AuthenticatedAccount(ACCOUNT_ID, "student"));
        return session;
    }
}
