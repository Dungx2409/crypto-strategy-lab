package com.cryptolab.experiment.application;

import java.util.UUID;

public final class ExperimentNotFoundException extends RuntimeException {

    public ExperimentNotFoundException(UUID experimentId) {
        super("Experiment not found: " + experimentId);
    }
}
