package com.cryptolab.experiment.application;

import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExperimentPlan;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetChecksum;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SingleExperimentCommand;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

public final class ExperimentPlanFactory {

    private final String evaluatorVersion;
    private final String codeCommit;
    private final String buildVersion;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public ExperimentPlanFactory(
            String evaluatorVersion,
            String codeCommit,
            String buildVersion,
            Clock clock,
            Supplier<UUID> idGenerator) {
        this.evaluatorVersion = requireText(evaluatorVersion, "evaluatorVersion");
        this.codeCommit = requireText(codeCommit, "codeCommit");
        this.buildVersion = requireText(buildVersion, "buildVersion");
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public ExperimentPlan create(SingleExperimentCommand command) {
        String checksum = MarketDatasetChecksum.calculate(command.candles());
        MarketDatasetRef reference = new MarketDatasetRef(
                command.symbol(),
                command.timeframe(),
                command.candles().getFirst().openTime(),
                command.candles().getLast().openTime().plus(command.timeframe().duration()),
                command.datasetVersion(),
                checksum);
        MarketDataset dataset = new MarketDataset(idGenerator.get(), reference, command.candles());
        UUID candidateId = idGenerator.get();
        CandidateStrategy candidate = new CandidateStrategy(
                candidateId,
                command.strategies(),
                command.combinationPolicy(),
                CandidateCanonicalizer.hash(command.strategies(), command.combinationPolicy()));
        return new ExperimentPlan(
                idGenerator.get(),
                idGenerator.get(),
                candidate,
                dataset,
                command.executionConfig(),
                command.generator(),
                evaluatorVersion,
                codeCommit,
                buildVersion,
                null,
                clock.instant());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
