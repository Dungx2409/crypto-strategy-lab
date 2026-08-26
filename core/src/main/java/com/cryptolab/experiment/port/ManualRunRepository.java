package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.ManualRunBatch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManualRunRepository {

    ManualRunBatch create(ManualRunBatch batch);

    Optional<ManualRunBatch> find(UUID accountId, UUID batchId);

    Optional<ManualRunBatch> find(UUID batchId);

    List<ManualRunBatch> findAll(UUID accountId);

    List<ManualRunBatch> findRecoverable();

    void markRunning(UUID batchId, Instant at);

    void completeChild(UUID childId, UUID experimentId, Instant at);

    void failChild(UUID childId, String failureMessage, Instant at);

    void finish(UUID batchId, Instant at);

    void requestCancellation(UUID accountId, UUID batchId, Instant at);
}
