package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.port.StrategyFactory;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class SpringStrategyRegistry implements StrategyRegistry {

    private final ConcurrentMap<PluginKey, StrategyFactory> factories = new ConcurrentHashMap<>();

    public SpringStrategyRegistry(List<StrategyFactory> discoveredFactories) {
        discoveredFactories.forEach(this::register);
    }

    @Override
    public void register(StrategyFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        PluginKey key = PluginKey.of(factory.type(), factory.version());
        StrategyFactory existing = factories.putIfAbsent(key, factory);
        if (existing != null) {
            throw new IllegalStateException(
                    "strategy factory already registered: " + key.type() + "@" + key.version());
        }
    }

    @Override
    public Strategy create(StrategyDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        PluginKey key = PluginKey.of(definition.type(), definition.version());
        StrategyFactory factory = factories.get(key);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "strategy factory is not registered: " + key.type() + "@" + key.version());
        }
        return factory.create(definition);
    }

    @Override
    public Set<String> registeredTypes() {
        return factories.keySet().stream()
                .map(PluginKey::type)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<StrategyPluginDescriptor> availableStrategies() {
        return factories.values().stream()
                .map(factory -> new StrategyPluginDescriptor(
                        factory.type(), factory.version(), factory.parameterSchema()))
                .sorted(Comparator.comparing(StrategyPluginDescriptor::type)
                        .thenComparing(StrategyPluginDescriptor::version))
                .toList();
    }

    private record PluginKey(String type, String version) {

        private static PluginKey of(String type, String version) {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("strategy factory type must not be blank");
            }
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("strategy factory version must not be blank");
            }
            return new PluginKey(type.trim().toUpperCase(Locale.ROOT), version.trim());
        }
    }
}
