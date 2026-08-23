package com.cryptolab.strategy.application;

import java.util.UUID;

public final class StrategyDraftNotFoundException extends RuntimeException {
    public StrategyDraftNotFoundException(UUID draftId) {
        super("Strategy draft was not found: " + draftId);
    }
}
