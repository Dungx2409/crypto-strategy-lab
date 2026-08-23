package com.cryptolab.api.news;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.news.application.CrawlerTemplateService;
import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.domain.CrawlerTemplateVersion;
import com.cryptolab.news.port.CrawlerSelectorRepairModel;
import com.cryptolab.news.port.CrawlerTemplateRepository;
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

class CrawlerTemplateControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID TEMPLATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000043");
    private CrawlerTemplateRepository repository;
    private CrawlerSelectorRepairModel repairModel;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        repository = mock(CrawlerTemplateRepository.class);
        repairModel = mock(CrawlerSelectorRepairModel.class);
        CrawlerTemplateService service = new CrawlerTemplateService(
                repository, repairModel, clock, () -> TEMPLATE_ID);
        mockMvc = MockMvcBuilders.standaloneSetup(new CrawlerTemplateController(service))
                .setControllerAdvice(new NewsExceptionHandler(clock))
                .build();
    }

    @Test
    void storesSelectorsUnderTheSessionAccount() throws Exception {
        mockMvc.perform(post("/api/v1/crawler-templates")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteUrl":"https://news.example.com","itemSelector":"article",
                                 "titleSelector":"h2","linkSelector":"a","dateSelector":"time"}
                                """))
                .andExpect(status().isCreated());

        verify(repository).create(
                eq(TEMPLATE_ID), eq(ACCOUNT_ID), eq("https://news.example.com"),
                eq(new CrawlerSelectors("article", "h2", "a", "time")), any());
    }

    @Test
    void reportsThatGeminiRepairIsUnavailableWhileTheKeyIsBlank() throws Exception {
        CrawlerSelectors selectors = new CrawlerSelectors("article", "h2", "a", "time");
        CrawlerTemplateVersion current = new CrawlerTemplateVersion(
                TEMPLATE_ID, ACCOUNT_ID, "https://news.example.com", 1, selectors,
                "ACTIVE", null, Instant.parse("2026-08-23T00:00:00Z"));
        when(repository.findCurrent(ACCOUNT_ID, TEMPLATE_ID)).thenReturn(java.util.Optional.of(current));
        doThrow(new IllegalStateException("GEMINI_API_KEY is blank; set it before using strategy authoring"))
                .when(repairModel).repair(any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/crawler-templates/{id}/repair", TEMPLATE_ID)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleHtml\":\"<article></article>\",\"failure\":\"no match\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GEMINI_NOT_CONFIGURED"));
    }

    private static MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedAccount.class.getName(),
                new AuthenticatedAccount(ACCOUNT_ID, "student"));
        return session;
    }
}
