package com.cryptolab.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.ContinuousDiscoveryService;
import com.cryptolab.experiment.domain.DiscoverySchedule;
import com.cryptolab.experiment.domain.DiscoveryScheduleStatus;
import com.cryptolab.experiment.port.DiscoveryScheduleRepository;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DiscoveryScheduleControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID SCHEDULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000043");
    private DiscoveryScheduleRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
        repository = mock(DiscoveryScheduleRepository.class);
        when(repository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ContinuousDiscoveryService service = new ContinuousDiscoveryService(
                repository, null, null, null, null, clock, () -> SCHEDULE_ID);
        mockMvc = MockMvcBuilders.standaloneSetup(new DiscoveryScheduleController(service))
                .setControllerAdvice(new DiscoveryScheduleExceptionHandler(clock))
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
    }

    @Test
    void mapsAnActiveRunEditToAConflict() throws Exception {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        DiscoverySchedule running = new DiscoverySchedule(
                SCHEDULE_ID, ACCOUNT_ID, "BTCUSDT", Timeframe.H1, Duration.ofDays(365),
                new BigDecimal("10000"), 125, Duration.ofHours(24), DiscoveryScheduleStatus.ACTIVE,
                now, UUID.randomUUID(), 0, null, now, now);
        when(repository.find(ACCOUNT_ID, SCHEDULE_ID)).thenReturn(java.util.Optional.of(running));

        mockMvc.perform(put("/api/v1/discovery-schedules/{id}", SCHEDULE_ID)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"BTCUSDT\",\"timeframe\":\"1h\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISCOVERY_SCHEDULE_STATE_CONFLICT"));
    }

    private static MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedAccount.class.getName(),
                new AuthenticatedAccount(ACCOUNT_ID, "student"));
        return session;
    }
}
