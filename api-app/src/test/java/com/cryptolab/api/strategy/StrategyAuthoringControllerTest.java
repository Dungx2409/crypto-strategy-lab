package com.cryptolab.api.strategy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.strategy.application.StrategyAuthoringService;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyDraft;
import com.cryptolab.strategy.domain.StrategyDraftStatus;
import com.cryptolab.strategy.domain.UserStrategy;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import com.cryptolab.strategy.port.StrategyAuthoringModel;
import com.cryptolab.strategy.port.StrategyDocumentDecoder;
import com.cryptolab.strategy.port.StrategyRegistry;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StrategyAuthoringControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID DRAFT_ID = UUID.fromString("00000000-0000-0000-0000-000000000043");
    private StrategyAuthoringModel model;
    private UserStrategyRepository repository;
    private StrategyDocumentDecoder decoder;
    private StrategyRegistry registry;
    private CombinationPolicyResolver policies;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        model = mock(StrategyAuthoringModel.class);
        repository = mock(UserStrategyRepository.class);
        decoder = mock(StrategyDocumentDecoder.class);
        registry = mock(StrategyRegistry.class);
        policies = mock(CombinationPolicyResolver.class);
        when(registry.availableStrategies()).thenReturn(java.util.List.of());
        when(model.proposeIdea(anyString(), anyList())).thenReturn("Use RSI with MA confirmation");
        when(repository.createDraft(any())).thenAnswer(invocation -> invocation.getArgument(0));
        StrategyAuthoringService service = new StrategyAuthoringService(
                model,
                decoder,
                repository,
                registry,
                policies,
                clock,
                () -> DRAFT_ID);
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

    @Test
    void buildsPreviewBeforeConfirmingTheSavedVersion() throws Exception {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        String source = "BUY WHEN CLOSE > SMA(CLOSE, 20) SELL WHEN CLOSE < SMA(CLOSE, 20)";
        UserStrategyDocument document = new UserStrategyDocument(
                "Generated trend",
                "A generated strategy",
                List.of(new StrategyDefinition("AI_DSL", "1.0", Map.of("source", source))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO));
        StrategyDraft pending = new StrategyDraft(
                DRAFT_ID, ACCOUNT_ID, "Generate a trend strategy", "Use an SMA crossover",
                StrategyDraftStatus.IDEA_PENDING_CONFIRMATION, null, null, now, now);
        StrategyDraft ready = new StrategyDraft(
                DRAFT_ID, ACCOUNT_ID, pending.prompt(), pending.idea(),
                StrategyDraftStatus.CODE_READY_FOR_CONFIRMATION, document, null, now, now);
        UserStrategy saved = new UserStrategy(DRAFT_ID, ACCOUNT_ID, 1, document, pending.prompt(), now);
        when(repository.findDraft(ACCOUNT_ID, DRAFT_ID))
                .thenReturn(Optional.of(pending), Optional.of(ready), Optional.of(ready));
        when(model.generateJson(any(), any(), any(), any(), any())).thenReturn("{generated}");
        when(decoder.decode("{generated}")).thenReturn(document);
        when(registry.create(any())).thenReturn(mock(com.cryptolab.strategy.domain.Strategy.class));
        when(repository.publishVersion(DRAFT_ID, ACCOUNT_ID, DRAFT_ID, now)).thenReturn(saved);

        mockMvc.perform(post("/api/v1/user-strategies/drafts/{id}/build", DRAFT_ID)
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CODE_READY_FOR_CONFIRMATION"))
                .andExpect(jsonPath("$.preview.strategies[0].type").value("AI_DSL"))
                .andExpect(jsonPath("$.preview.strategies[0].parameters.source").value(source));
        verify(repository, never()).publishVersion(any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/user-strategies/drafts/{id}/confirm", DRAFT_ID)
                        .session(authenticatedSession()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.name").value("Generated trend"));
    }

    private static MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedAccount.class.getName(),
                new AuthenticatedAccount(ACCOUNT_ID, "student"));
        return session;
    }
}
