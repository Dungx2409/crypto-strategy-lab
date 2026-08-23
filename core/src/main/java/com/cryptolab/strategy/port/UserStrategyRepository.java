package com.cryptolab.strategy.port;

import com.cryptolab.strategy.domain.StrategyDraft;
import com.cryptolab.strategy.domain.StrategyDraftStatus;
import com.cryptolab.strategy.domain.UserStrategy;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStrategyRepository {

    StrategyDraft createDraft(StrategyDraft draft);

    Optional<StrategyDraft> findDraft(UUID accountId, UUID draftId);

    void updateDraft(UUID accountId, UUID draftId, StrategyDraftStatus status, String failureMessage, Instant updatedAt);

    UserStrategy saveVersion(
            UUID id,
            UUID accountId,
            UserStrategyDocument document,
            String sourcePrompt,
            Instant createdAt);

    List<UserStrategy> findAll(UUID accountId);

    Optional<UserStrategy> find(UUID accountId, UUID strategyId);

    boolean delete(UUID accountId, UUID strategyId);
}
