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
import com.cryptolab.strategy.domain.extension.AiDslStrategy;
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
    void buildsTestedPreviewThenConfirmsAndStoresAccountOwnedVersion() {
        StrategyAuthoringModel model = mock(StrategyAuthoringModel.class);
        StrategyDocumentDecoder decoder = mock(StrategyDocumentDecoder.class);
        StrategyRegistry registry = mock(StrategyRegistry.class);
        CombinationPolicyResolver policies = mock(CombinationPolicyResolver.class);
        InMemoryRepository repository = new InMemoryRepository();
        UserStrategyDocument document = new UserStrategyDocument(
                "Trend idea", "Use generated Trading DSL",
                List.of(new StrategyDefinition("AI_DSL", "1.0", Map.of(
                        "source", "BUY WHEN CLOSE > SMA(CLOSE, 20) SELL WHEN CLOSE < SMA(CLOSE, 20)"))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO));
        UserStrategyDocument invalidDocument = new UserStrategyDocument(
                "Trend idea", "Invalid generated Trading DSL",
                List.of(new StrategyDefinition("AI_DSL", "1.0", Map.of(
                        "source", "BUY WHEN FILE_READ == 1 SELL WHEN CLOSE < 0"))),
                new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO));
        when(model.proposeIdea(any(), any())).thenReturn("Use a fast and slow moving average.");
        when(model.generateJson(any(), any(), any(), any(), any()))
                .thenReturn("{invalid-dsl}", "{valid}");
        when(decoder.decode("{invalid-dsl}")).thenReturn(invalidDocument);
        when(decoder.decode("{valid}")).thenReturn(document);
        when(registry.create(any())).thenAnswer(invocation -> {
            StrategyDefinition definition = invocation.getArgument(0);
            return new AiDslStrategy(definition.parameters().get("source").toString());
        });

        List<UUID> ids = new ArrayList<>(List.of(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001")));
        StrategyAuthoringService service = new StrategyAuthoringService(
                model, decoder, repository, registry, policies,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> ids.removeFirst());

        StrategyDraft draft = service.propose(ACCOUNT_ID, "Build a trend strategy");
        StrategyDraft built = service.build(ACCOUNT_ID, draft.id());

        assertThat(built.status()).isEqualTo(StrategyDraftStatus.CODE_READY_FOR_CONFIRMATION);
        assertThat(built.preview()).isEqualTo(document);
        assertThat(repository.strategies).isEmpty();

        UserStrategy saved = service.confirm(ACCOUNT_ID, draft.id());

        assertThat(draft.status()).isEqualTo(StrategyDraftStatus.IDEA_PENDING_CONFIRMATION);
        assertThat(saved.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(repository.drafts.get(draft.id()).status()).isEqualTo(StrategyDraftStatus.READY);
        verify(model).generateJson(any(), any(), any(), org.mockito.ArgumentMatchers.eq("{invalid-dsl}"),
                org.mockito.ArgumentMatchers.contains("Unsupported AI DSL word"));
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
                UUID accountId,
                UUID draftId,
                StrategyDraftStatus status,
                UserStrategyDocument preview,
                String failure,
                Instant updatedAt) {
            StrategyDraft old = drafts.get(draftId);
            drafts.put(draftId, new StrategyDraft(
                    old.id(), old.accountId(), old.prompt(), old.idea(), status,
                    preview, failure, old.createdAt(), updatedAt));
        }

        @Override
        public UserStrategy publishVersion(
                UUID id, UUID accountId, UUID draftId, Instant createdAt) {
            StrategyDraft draft = drafts.get(draftId);
            UserStrategy strategy = new UserStrategy(
                    id, accountId, 1, draft.preview(), draft.prompt(), createdAt);
            strategies.put(id, strategy);
            updateDraft(
                    accountId,
                    draftId,
                    StrategyDraftStatus.READY,
                    draft.preview(),
                    null,
                    createdAt);
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
