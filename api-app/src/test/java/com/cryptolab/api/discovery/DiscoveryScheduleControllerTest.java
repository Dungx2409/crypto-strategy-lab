package com.cryptolab.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.ContinuousDiscoveryService;
import com.cryptolab.experiment.domain.DiscoverySchedule;
import com.cryptolab.experiment.domain.DiscoveryScheduleStatus;
import com.cryptolab.experiment.domain.DiscoveryScheduleVersion;
import com.cryptolab.experiment.port.DiscoveryScheduleRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DiscoveryScheduleControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID SCHEDULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000043");
    private static final UUID LAST_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000044");
    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    private DiscoveryScheduleRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = mock(DiscoveryScheduleRepository.class);
        when(repository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ContinuousDiscoveryService service = new ContinuousDiscoveryService(
                repository, null, null, null, null, clock, () -> SCHEDULE_ID);
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new DiscoveryScheduleController(service))
                .setControllerAdvice(new DiscoveryScheduleExceptionHandler(clock))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void createsTheDefaultTwentyFourHourGeneticScheduleForTheAccount() throws Exception {
        mockMvc.perform(post("/api/v1/discovery-schedules")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"BTCUSDT\",\"timeframe\":\"1h\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<DiscoverySchedule> captor = ArgumentCaptor.forClass(DiscoverySchedule.class);
        verify(repository).create(captor.capture());
        DiscoverySchedule created = captor.getValue();
        assertThat(created.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(created.lookback()).isEqualTo(Duration.ofDays(365));
        assertThat(created.initialCapital()).isEqualByComparingTo("10000");
        assertThat(created.candidateLimit()).isEqualTo(125);
        assertThat(created.interval()).isEqualTo(Duration.ofHours(24));
        assertThat(created.lastSearchRunId()).isNull();
    }

    @Test
    void listsSchedulesIncludingTheLastCompletedSearchRun() throws Exception {
        when(repository.findAll(ACCOUNT_ID)).thenReturn(List.of(schedule(LAST_RUN_ID)));

        mockMvc.perform(get("/api/v1/discovery-schedules").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SCHEDULE_ID.toString()))
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$[0].lastSearchRunId").value(LAST_RUN_ID.toString()))
                .andExpect(jsonPath("$[0].completedRuns").value(3))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void returnsImmutableConfigurationVersionsNewestFirst() throws Exception {
        when(repository.find(ACCOUNT_ID, SCHEDULE_ID)).thenReturn(Optional.of(schedule(null)));
        when(repository.findVersions(ACCOUNT_ID, SCHEDULE_ID)).thenReturn(List.of(
                new DiscoveryScheduleVersion(
                        SCHEDULE_ID, 2, "ETHUSDT", Timeframe.H4, Duration.ofDays(90),
                        new BigDecimal("25000"), 250, Duration.ofHours(12), NOW.plusSeconds(60)),
                new DiscoveryScheduleVersion(
                        SCHEDULE_ID, 1, "BTCUSDT", Timeframe.H1, Duration.ofDays(365),
                        new BigDecimal("10000"), 125, Duration.ofHours(24), NOW)));

        mockMvc.perform(get("/api/v1/discovery-schedules/{id}/versions", SCHEDULE_ID)
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].symbol").value("ETHUSDT"))
                .andExpect(jsonPath("$[0].candidateLimit").value(250))
                .andExpect(jsonPath("$[1].version").value(1))
                .andExpect(jsonPath("$[1].symbol").value("BTCUSDT"));

        verify(repository).findVersions(eq(ACCOUNT_ID), eq(SCHEDULE_ID));
    }

    @Test
    void mapsAnActiveRunEditToAConflict() throws Exception {
        DiscoverySchedule running = new DiscoverySchedule(
                SCHEDULE_ID, ACCOUNT_ID, "BTCUSDT", Timeframe.H1, Duration.ofDays(365),
                new BigDecimal("10000"), 125, Duration.ofHours(24), DiscoveryScheduleStatus.ACTIVE,
                NOW, UUID.randomUUID(), LAST_RUN_ID, 0, null, NOW, NOW);
        when(repository.find(ACCOUNT_ID, SCHEDULE_ID)).thenReturn(Optional.of(running));

        mockMvc.perform(put("/api/v1/discovery-schedules/{id}", SCHEDULE_ID)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"BTCUSDT\",\"timeframe\":\"1h\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISCOVERY_SCHEDULE_STATE_CONFLICT"));
    }

    private static DiscoverySchedule schedule(UUID lastSearchRunId) {
        return new DiscoverySchedule(
                SCHEDULE_ID, ACCOUNT_ID, "BTCUSDT", Timeframe.H1, Duration.ofDays(365),
                new BigDecimal("10000"), 125, Duration.ofHours(24), DiscoveryScheduleStatus.ACTIVE,
                NOW, null, lastSearchRunId, lastSearchRunId == null ? 0 : 3, null, NOW, NOW);
    }

    private static MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedAccount.class.getName(),
                new AuthenticatedAccount(ACCOUNT_ID, "student"));
        return session;
    }
}
