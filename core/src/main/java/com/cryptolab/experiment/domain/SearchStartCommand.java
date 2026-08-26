package com.cryptolab.experiment.domain;

import java.util.Objects;
import java.util.UUID;

public record SearchStartCommand(
        SearchContext context,
        ExecutionConfig executionConfig,
        UUID ownerAccountId,
        SearchRunKind runKind) {

    public SearchStartCommand(SearchContext context, ExecutionConfig executionConfig) {
        this(context, executionConfig, null, SearchRunKind.LEGACY);
    }

    public SearchStartCommand {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(executionConfig, "executionConfig must not be null");
        Objects.requireNonNull(runKind, "runKind must not be null");
        if (runKind != SearchRunKind.LEGACY && ownerAccountId == null) {
            throw new IllegalArgumentException("owned search runs require an account id");
        }
    }
}
