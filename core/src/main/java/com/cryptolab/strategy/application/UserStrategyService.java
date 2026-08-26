package com.cryptolab.strategy.application;

import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.strategy.domain.UserStrategy;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import com.cryptolab.strategy.port.StrategyRegistry;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class UserStrategyService {

    private final UserStrategyRepository repository;
    private final StrategyRegistry registry;
    private final CombinationPolicyResolver policies;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public UserStrategyService(
            UserStrategyRepository repository,
            StrategyRegistry registry,
            CombinationPolicyResolver policies,
            Clock clock,
            Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository);
        this.registry = Objects.requireNonNull(registry);
        this.policies = Objects.requireNonNull(policies);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    public UserStrategy save(UUID accountId, UserStrategyDocument document) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(document, "document must not be null");
        document.strategies().forEach(registry::create);
        policies.resolve(document.combinationPolicy());
        return repository.saveVersion(ids.get(), accountId, document, "manual", clock.instant());
    }

    public List<UserStrategy> list(UUID accountId) {
        return repository.findAll(accountId);
    }

    public UserStrategy get(UUID accountId, UUID strategyId) {
        return repository.find(accountId, strategyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User strategy was not found: " + strategyId));
    }

    public void delete(UUID accountId, UUID strategyId) {
        if (!repository.delete(accountId, strategyId)) {
            throw new IllegalArgumentException("User strategy was not found: " + strategyId);
        }
    }
}
