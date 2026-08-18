package com.cryptolab.experiment.application;

import java.util.UUID;

public final class SearchRunNotFoundException extends RuntimeException {

    public SearchRunNotFoundException(UUID searchRunId) {
        super("search run not found: " + searchRunId);
    }
}
