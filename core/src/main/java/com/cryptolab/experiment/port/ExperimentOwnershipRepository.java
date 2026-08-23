package com.cryptolab.experiment.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentOwnershipRepository {

    void assign(UUID experimentId, UUID accountId, Instant createdAt);

    Optional<UUID> ownerOf(UUID experimentId);

    List<UUID> findExperimentIds(UUID accountId);

    static ExperimentOwnershipRepository none() {
        return new ExperimentOwnershipRepository() {
            public void assign(UUID experimentId, UUID accountId, Instant createdAt) {}
            public Optional<UUID> ownerOf(UUID experimentId) { return Optional.empty(); }
            public List<UUID> findExperimentIds(UUID accountId) { return List.of(); }
        };
    }
}
