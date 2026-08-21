package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.port.StrategyGenerator;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.cryptolab.strategy.port.StrategyRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A deliberately small deterministic genetic generator used to prove generator replaceability.
 * Genome-hash ordering is the deterministic parent-selection fitness policy; trading fitness is
 * still calculated only by the experiment evaluator and never by this generator.
 */
public final class GeneticStrategyGenerator implements StrategyGenerator {

    public static final String TYPE = "genetic";
    public static final String VERSION = "1.0";
    public static final int POPULATION_SIZE = 20;
    public static final int MUTATION_PERCENT = 20;

    private final StrategyRegistry registry;

    public GeneticStrategyGenerator(StrategyRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
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
        Objects.requireNonNull(context, "context must not be null");
        return StreamSupport.stream(new GeneticSpliterator(context), false);
    }

    private final class GeneticSpliterator
            extends Spliterators.AbstractSpliterator<CandidateStrategy> {

        private final SearchContext context;
        private final SplittableRandom random;
        private List<CandidateStrategy> population;
        private int generation;
        private int slot;

        private GeneticSpliterator(SearchContext context) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE);
            this.context = context;
            this.random = new SplittableRandom(context.randomSeed());
        }

        @Override
        public boolean tryAdvance(Consumer<? super CandidateStrategy> action) {
            if (population == null) {
                population = initialPopulation(context);
                if (population.isEmpty()) {
                    return false;
                }
            } else if (slot == population.size()) {
                population = evolve(population, ++generation, context, random);
                slot = 0;
            }
            action.accept(withGeneticIdentity(population.get(slot), generation, slot, context));
            slot++;
            return true;
        }
    }

    private List<CandidateStrategy> initialPopulation(SearchContext context) {
        try (Stream<CandidateStrategy> seed = new RandomStrategyGenerator(registry).generate(context)) {
            return seed.limit(POPULATION_SIZE).toList();
        }
    }

    private List<CandidateStrategy> evolve(
            List<CandidateStrategy> current,
            int generation,
            SearchContext context,
            SplittableRandom random) {
        List<CandidateStrategy> parents = current.stream()
                .sorted(Comparator.comparing(CandidateStrategy::candidateHash).reversed())
                .limit(Math.max(1, current.size() / 2))
                .toList();
        List<CandidateStrategy> next = new ArrayList<>(current.size());
        for (int index = 0; index < current.size(); index++) {
            CandidateStrategy left = parents.get(index % parents.size());
            CandidateStrategy right = parents.get(random.nextInt(parents.size()));
            List<StrategyDefinition> genome = crossover(
                    left.strategies(), right.strategies(), context.strategyTypes(), random);
            if (random.nextInt(100) < MUTATION_PERCENT) {
                genome = mutate(genome, context, random);
            }
            if (!valid(genome)) {
                genome = left.strategies();
            }
            String hash = CandidateCanonicalizer.hash(genome, context.combinationPolicy());
            UUID id = geneticId(context.searchRunId(), generation, index, hash);
            next.add(new CandidateStrategy(id, genome, context.combinationPolicy(), hash));
        }
        return List.copyOf(next);
    }

    private static List<StrategyDefinition> crossover(
            List<StrategyDefinition> left,
            List<StrategyDefinition> right,
            List<String> strategyTypes,
            SplittableRandom random) {
        Map<String, StrategyDefinition> leftByType = definitionsByType(left);
        Map<String, StrategyDefinition> rightByType = definitionsByType(right);
        List<StrategyDefinition> child = new ArrayList<>(strategyTypes.size());
        for (String strategyType : strategyTypes) {
            StrategyDefinition first = leftByType.get(strategyType);
            StrategyDefinition second = rightByType.get(strategyType);
            if (first == null || second == null) {
                StrategyDefinition available = first != null ? first : second;
                if (available != null && random.nextBoolean()) {
                    child.add(available);
                }
                continue;
            }
            Map<String, Object> parameters = new LinkedHashMap<>();
            first.parameters().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parameters.put(
                            entry.getKey(),
                            random.nextBoolean()
                                    ? entry.getValue()
                                    : second.parameters().getOrDefault(entry.getKey(), entry.getValue())));
            child.add(new StrategyDefinition(first.type(), first.version(), parameters));
        }
        return child.isEmpty() ? left : List.copyOf(child);
    }

    private static Map<String, StrategyDefinition> definitionsByType(List<StrategyDefinition> definitions) {
        Map<String, StrategyDefinition> byType = new LinkedHashMap<>();
        definitions.forEach(definition -> byType.put(definition.type(), definition));
        return byType;
    }

    private static List<StrategyDefinition> mutate(
            List<StrategyDefinition> genome,
            SearchContext context,
            SplittableRandom random) {
        if (genome.size() > 1 && random.nextBoolean()) {
            List<StrategyDefinition> mutated = new ArrayList<>(genome);
            mutated.remove(random.nextInt(mutated.size()));
            return List.copyOf(mutated);
        }
        List<Integer> mutable = new ArrayList<>();
        for (int index = 0; index < genome.size(); index++) {
            StrategyDefinition definition = genome.get(index);
            boolean hasChoice = definition.parameters().keySet().stream()
                    .anyMatch(parameter -> !context.parameterSpace()
                            .choices(definition.type(), parameter)
                            .isEmpty());
            if (hasChoice) {
                mutable.add(index);
            }
        }
        if (mutable.isEmpty()) {
            return genome;
        }
        int strategyIndex = mutable.get(random.nextInt(mutable.size()));
        StrategyDefinition source = genome.get(strategyIndex);
        List<String> parameters = source.parameters().keySet().stream()
                .filter(parameter -> !context.parameterSpace()
                        .choices(source.type(), parameter)
                        .isEmpty())
                .sorted()
                .toList();
        String parameter = parameters.get(random.nextInt(parameters.size()));
        List<Object> choices = context.parameterSpace().choices(source.type(), parameter);
        Map<String, Object> mutatedParameters = new LinkedHashMap<>(source.parameters());
        mutatedParameters.put(parameter, choices.get(random.nextInt(choices.size())));
        List<StrategyDefinition> mutated = new ArrayList<>(genome);
        mutated.set(
                strategyIndex,
                new StrategyDefinition(source.type(), source.version(), mutatedParameters));
        return List.copyOf(mutated);
    }

    private boolean valid(List<StrategyDefinition> definitions) {
        try {
            definitions.forEach(registry::create);
            return true;
        } catch (IllegalArgumentException invalidCombination) {
            return false;
        }
    }

    private static CandidateStrategy withGeneticIdentity(
            CandidateStrategy candidate,
            int generation,
            int slot,
            SearchContext context) {
        return new CandidateStrategy(
                geneticId(context.searchRunId(), generation, slot, candidate.candidateHash()),
                candidate.strategies(),
                candidate.combinationPolicy(),
                candidate.candidateHash());
    }

    private static UUID geneticId(UUID searchRunId, int generation, int slot, String hash) {
        return UUID.nameUUIDFromBytes(
                (searchRunId + ":genetic:" + generation + ':' + slot + ':' + hash)
                        .getBytes(StandardCharsets.UTF_8));
    }
}
