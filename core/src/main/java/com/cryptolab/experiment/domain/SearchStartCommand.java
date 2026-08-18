package com.cryptolab.experiment.domain;

import java.util.Objects;

public record SearchStartCommand(SearchContext context, ExecutionConfig executionConfig) {

    public SearchStartCommand {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(executionConfig, "executionConfig must not be null");
    }
}
