package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.DiscoverySchedule;
import com.cryptolab.experiment.domain.DiscoveryScheduleVersion;
import com.cryptolab.marketdata.domain.Timeframe;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscoveryScheduleRepository {

    DiscoverySchedule create(DiscoverySchedule schedule);

    List<DiscoverySchedule> findAll(UUID accountId);

    Optional<DiscoverySchedule> find(UUID accountId, UUID scheduleId);

    List<DiscoverySchedule> findRunning();

    List<DiscoverySchedule> findDue(Instant now, int limit);

    boolean claim(UUID scheduleId, UUID searchRunId, Instant nextRunAt, Instant updatedAt);

    void completeRun(UUID scheduleId, Instant updatedAt);

    void failRun(UUID scheduleId, String error, Instant updatedAt);

    boolean stop(UUID accountId, UUID scheduleId, Instant updatedAt);

    boolean start(UUID accountId, UUID scheduleId, Instant nextRunAt, Instant updatedAt);

    DiscoverySchedule updateConfiguration(
            UUID accountId,
            UUID scheduleId,
            String symbol,
            Timeframe timeframe,
            Duration lookback,
            BigDecimal initialCapital,
            long candidateLimit,
            Duration interval,
            Instant updatedAt);

    List<DiscoveryScheduleVersion> findVersions(UUID accountId, UUID scheduleId);

    void recoverInterrupted(Instant now);
}
