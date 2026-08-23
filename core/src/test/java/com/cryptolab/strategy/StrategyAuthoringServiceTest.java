package com.cryptolab.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StrategyAuthoringServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

    @Test
    void requiresIdeaConfirmationThenRepairsInvalidJsonAndStoresAccountOwnedVersion() {
        StrategyAuthoringModel model = mock(StrategyAuthoringModel.class);
        StrategyDocumentDecoder decoder = mock(StrategyDocumentDecoder.class);
        StrategyRegistry registry = mock(StrategyRegistry.class);
        CombinationPolicyResolver policies = mock(CombinationPolicyResolver.class);
        InMemoryRepository repository = new InMemoryRepository();
        var executable = mock(com.cryptolab.strategy.domain.Strategy.class);
        UserStrategyDocument document = new UserStrategyDocument(
                "Trend idea", "Use moving averages",
                List.of(new StrategyDefinition("MOVING_AVERAGE", "1.0", Map.of())),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO));
        when(model.proposeIdea(any(), any())).thenReturn("Use a fast and slow moving average.");
        when(model.generateJson(any(), any(), any(), any(), any()))
                .thenReturn("bad-json", "{valid}");
        when(decoder.decode("bad-json")).thenThrow(new IllegalArgumentException("invalid JSON"));
        when(decoder.decode("{valid}")).thenReturn(document);
        when(registry.create(any())).thenReturn(executable);

        List<UUID> ids = new ArrayList<>(List.of(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001")));
        StrategyAuthoringService service = new StrategyAuthoringService(
                model, decoder, repository, registry, policies,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> ids.removeFirst());

        StrategyDraft draft = service.propose(ACCOUNT_ID, "Build a trend strategy");
        UserStrategy saved = service.confirm(ACCOUNT_ID, draft.id());

        assertThat(draft.status()).isEqualTo(StrategyDraftStatus.IDEA_PENDING_CONFIRMATION);
        assertThat(saved.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(repository.drafts.get(draft.id()).status()).isEqualTo(StrategyDraftStatus.READY);
        verify(model).generateJson(any(), any(), any(), org.mockito.ArgumentMatchers.eq("bad-json"),
                org.mockito.ArgumentMatchers.eq("invalid JSON"));
        verify(executable).analyze(any());
        verify(policies).resolve(document.combinationPolicy());
    }

    private static final class InMemoryRepository implements UserStrategyRepository {
        private final Map<UUID, StrategyDraft> drafts = new HashMap<>();
        private final Map<UUID, UserStrategy> strategies = new HashMap<>();

        @Override
        public StrategyDraft createDraft(StrategyDraft draft) {
            drafts.put(draft.id(), draft);
            return draft;
        }

        @Override
        public Optional<StrategyDraft> findDraft(UUID accountId, UUID draftId) {
            return Optional.ofNullable(drafts.get(draftId)).filter(item -> item.accountId().equals(accountId));
        }

        @Override
        public void updateDraft(
                UUID accountId, UUID draftId, StrategyDraftStatus status, String failure, Instant updatedAt) {
            StrategyDraft old = drafts.get(draftId);
            drafts.put(draftId, new StrategyDraft(
                    old.id(), old.accountId(), old.prompt(), old.idea(), status,
                    failure, old.createdAt(), updatedAt));
        }

        @Override
        public UserStrategy saveVersion(
                UUID id, UUID accountId, UserStrategyDocument document, String prompt, Instant createdAt) {
            UserStrategy strategy = new UserStrategy(id, accountId, 1, document, prompt, createdAt);
            strategies.put(id, strategy);
            return strategy;
        }

        @Override
        public List<UserStrategy> findAll(UUID accountId) {
            return strategies.values().stream().filter(item -> item.accountId().equals(accountId)).toList();
        }

        @Override
        public Optional<UserStrategy> find(UUID accountId, UUID strategyId) {
            return Optional.ofNullable(strategies.get(strategyId)).filter(item -> item.accountId().equals(accountId));
        }

        @Override
        public boolean delete(UUID accountId, UUID strategyId) {
            return find(accountId, strategyId).map(item -> strategies.remove(strategyId) != null).orElse(false);
        }
    }
}
