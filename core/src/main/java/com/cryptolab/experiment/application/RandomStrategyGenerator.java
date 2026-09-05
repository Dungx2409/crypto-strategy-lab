package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.port.StrategyGenerator;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class RandomStrategyGenerator implements StrategyGenerator {

    public static final String TYPE = "random";
    public static final String VERSION = "1.0";

    private final StrategyRegistry registry;

    public RandomStrategyGenerator(StrategyRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Stream<CandidateStrategy> generate(SearchContext context) {
        List<StrategyTemplate> templates = templates(context);
        long combinations = combinationCount(templates);
        return StreamSupport.stream(new CandidateSpliterator(context, templates, combinations), false);
    }

    private List<StrategyTemplate> templates(SearchContext context) {
        Map<String, StrategyPluginDescriptor> descriptors = new LinkedHashMap<>();
        for (StrategyPluginDescriptor descriptor : registry.authoringStrategies()) {
            String expectedVersion = context.strategyVersions().get(descriptor.type());
            if (context.strategyTypes().contains(descriptor.type())
                    && descriptor.version().equals(expectedVersion)) {
                descriptors.put(descriptor.type(), descriptor);
            }
        }

        List<StrategyTemplate> templates = new ArrayList<>();
        for (String type : context.strategyTypes()) {
            StrategyPluginDescriptor descriptor = descriptors.get(type);
            if (descriptor == null) {
                throw new IllegalArgumentException(
                        "selected strategy is not registered: " + type + '@' + context.strategyVersions().get(type));
            }
            Map<String, List<Object>> configured = context.parameterSpace().values().getOrDefault(type, Map.of());
            if (!descriptor.parameterSchema().keySet().containsAll(configured.keySet())) {
                throw new IllegalArgumentException("parameterSpace contains an unknown parameter for " + type);
            }
            List<ParameterDimension> dimensions = descriptor.parameterSchema().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new ParameterDimension(
                            entry.getKey(), choices(context, descriptor.type(), entry.getKey(), entry.getValue())))
                    .toList();
            templates.add(new StrategyTemplate(
                    descriptor.type(),
                    descriptor.version(),
                    context.strategyLabels().get(descriptor.type()),
                    dimensions));
        }
        return List.copyOf(templates);
    }

    private static List<Object> choices(
            SearchContext context,
            String strategyType,
            String parameter,
            Object schemaValue) {
        List<Object> configured = context.parameterSpace().choices(strategyType, parameter);
        if (!configured.isEmpty()) {
            return configured;
        }
        if (schemaValue instanceof Map<?, ?> schema && schema.get("default") != null) {
            return List.of(schema.get("default"));
        }
        throw new IllegalArgumentException(
                "parameterSpace must define " + strategyType + '.' + parameter + " because it has no default");
    }

    private static long combinationCount(List<StrategyTemplate> templates) {
        long combinations = 1;
        try {
            for (StrategyTemplate template : templates) {
                combinations = Math.multiplyExact(
                        combinations,
                        Math.addExact(parameterCombinationCount(template), 1));
            }
            combinations = Math.subtractExact(combinations, 1);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("parameter space exceeds the supported deterministic range", exception);
        }
        return combinations;
    }

    private static long parameterCombinationCount(StrategyTemplate template) {
        long combinations = 1;
        for (ParameterDimension dimension : template.dimensions()) {
            combinations = Math.multiplyExact(combinations, dimension.choices().size());
        }
        return combinations;
    }

    private final class CandidateSpliterator extends Spliterators.AbstractSpliterator<CandidateStrategy> {

        private final SearchContext context;
        private final List<StrategyTemplate> templates;
        private final long total;
        private long inspected;
        private long currentIndex;
        private final long step;

        private CandidateSpliterator(SearchContext context, List<StrategyTemplate> templates, long total) {
            super(total, Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.DISTINCT);
            this.context = context;
            this.templates = templates;
            this.total = total;
            SplittableRandom random = new SplittableRandom(context.randomSeed());
            currentIndex = total == 1 ? 0 : random.nextLong(total);
            step = coprimeStep(random, total);
        }

        @Override
        public boolean tryAdvance(Consumer<? super CandidateStrategy> action) {
            while (inspected < total) {
                long combinationIndex = currentIndex;
                currentIndex = addModulo(currentIndex, step, total);
                inspected++;
                List<StrategyDefinition> definitions = definitions(combinationIndex, templates);
                try {
                    definitions.forEach(registry::create);
                } catch (IllegalArgumentException invalidCombination) {
                    continue;
                }
                String hash = CandidateCanonicalizer.hash(definitions, context.combinationPolicy());
                UUID candidateId = UUID.nameUUIDFromBytes(
                        (context.searchRunId() + ":" + combinationIndex + ":" + hash)
                                .getBytes(StandardCharsets.UTF_8));
                action.accept(new CandidateStrategy(
                        candidateId, definitions, context.combinationPolicy(), hash));
                return true;
            }
            return false;
        }
    }

    private static List<StrategyDefinition> definitions(
            long combinationIndex,
            List<StrategyTemplate> templates) {
        long remaining = combinationIndex + 1;
        List<StrategyDefinition> definitions = new ArrayList<>(templates.size());
        for (StrategyTemplate template : templates) {
            long parameterCombinations = parameterCombinationCount(template);
            long membershipChoice = remaining % (parameterCombinations + 1);
            remaining /= parameterCombinations + 1;
            if (membershipChoice == 0) {
                continue;
            }
            long parameterCombination = membershipChoice - 1;
            Map<String, Object> parameters = new LinkedHashMap<>();
            for (ParameterDimension dimension : template.dimensions()) {
                int choiceIndex = (int) (parameterCombination % dimension.choices().size());
                parameterCombination /= dimension.choices().size();
                parameters.put(dimension.name(), dimension.choices().get(choiceIndex));
            }
            definitions.add(new StrategyDefinition(
                    template.type(), template.version(), parameters, template.displayLabel()));
        }
        return List.copyOf(definitions);
    }

    private static long coprimeStep(SplittableRandom random, long total) {
        if (total == 1) {
            return 0;
        }
        long candidate;
        do {
            candidate = random.nextLong(1, total);
        } while (greatestCommonDivisor(candidate, total) != 1);
        return candidate;
    }

    private static long greatestCommonDivisor(long left, long right) {
        while (right != 0) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    private static long addModulo(long left, long right, long modulus) {
        if (modulus == 1) {
            return 0;
        }
        return left >= modulus - right ? left - (modulus - right) : left + right;
    }

    private record StrategyTemplate(
            String type,
            String version,
            String displayLabel,
            List<ParameterDimension> dimensions) {}

    private record ParameterDimension(String name, List<Object> choices) {}
}
