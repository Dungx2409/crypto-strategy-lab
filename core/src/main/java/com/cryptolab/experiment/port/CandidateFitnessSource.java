package com.cryptolab.experiment.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FunctionalInterface
public interface CandidateFitnessSource {

    Map<UUID, BigDecimal> awaitFitness(UUID searchRunId, List<UUID> candidateIds);
}
