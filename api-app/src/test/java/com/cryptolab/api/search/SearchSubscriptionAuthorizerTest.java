package com.cryptolab.api.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptolab.account.domain.AccountRole;
import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.experiment.application.SearchCoordinator;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunKind;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.SearchRunSummary;
import com.cryptolab.experiment.domain.StopConditionEvaluator;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.experiment.port.StrategyGenerator;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class SearchSubscriptionAuthorizerTest {

    @Test
    void authorizesPrivateTopicUsingTheAccountInsideSpringAuthentication() {
        SearchRunRepository repository = mock(SearchRunRepository.class);
        SearchCoordinator searches = coordinator(repository);
        SearchSubscriptionAuthorizer authorizer = new SearchSubscriptionAuthorizer(searches);
        UUID accountId = UUID.randomUUID();
        UUID searchId = UUID.randomUUID();
        AuthenticatedAccount account = new AuthenticatedAccount(accountId, "student", AccountRole.USER);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(account, null, List.of());
        when(repository.findSummary(searchId)).thenReturn(Optional.of(summary(searchId, accountId)));

        authorizer.preSend(subscription("/topic/search/" + searchId, authentication), null);

        verify(repository).findSummary(searchId);
    }

    @Test
    void rejectsPrivateTopicWithoutAnAuthenticatedAccount() {
        SearchSubscriptionAuthorizer authorizer = new SearchSubscriptionAuthorizer(
                coordinator(mock(SearchRunRepository.class)));

        assertThatThrownBy(() -> authorizer.preSend(
                        subscription("/topic/search/" + UUID.randomUUID(), null), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authentication is required");
    }

    private static Message<byte[]> subscription(String destination, java.security.Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static SearchCoordinator coordinator(SearchRunRepository repository) {
        StrategyGenerator generator = mock(StrategyGenerator.class);
        when(generator.type()).thenReturn("random");
        when(generator.version()).thenReturn("1.0");
        return new SearchCoordinator(
                generator,
                repository,
                new StopConditionEvaluator(),
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC),
                new JobDispatchMetadata("evaluator-v1", "test", "test"));
    }

    private static SearchRunSummary summary(UUID searchId, UUID accountId) {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        SearchContext context = new SearchContext(
                searchId,
                new MarketDatasetRef(
                        "BTCUSDT", Timeframe.M5, now.minusSeconds(300), now,
                        "test", "checksum"),
                List.of("TEST"),
                Map.of("TEST", "1.0"),
                new SearchParameterSpace(Map.of()),
                new CombinationPolicyDefinition(
                        "MAJORITY", "1.0", Map.of(), BigDecimal.ZERO),
                1,
                new StopConditions(1L, null, null),
                1);
        SearchRun run = new SearchRun(
                searchId, SearchRunStatus.RUNNING, context, "random", "1.0",
                now, now, null, false, accountId, SearchRunKind.SEARCH);
        return new SearchRunSummary(
                run, 0, 0, 0, 0, 0, 0, 0, 0,
                null, 0, null, null, null);
    }
}
