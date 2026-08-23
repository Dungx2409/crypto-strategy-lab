package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.SearchContext;
import java.util.stream.Stream;

public interface StrategyGenerator {

    String type();

    String version();

    Stream<CandidateStrategy> generate(SearchContext context);

    default Stream<CandidateStrategy> generate(
            SearchContext context,
            CandidateFitnessSource fitnessSource) {
        return generate(context);
    }

    default int generationSize(SearchContext context) {
        return Integer.MAX_VALUE;
    }
}
