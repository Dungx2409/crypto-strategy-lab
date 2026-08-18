package com.cryptolab.infrastructure.experiment.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.domain.SearchRunStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MicrometerSearchTelemetryTest {

    @Test
    void exposesActiveGeneratedAndTerminalSearchMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerSearchTelemetry telemetry = new MicrometerSearchTelemetry(registry);
        UUID runId = UUID.fromString("70000000-0000-0000-0000-000000000002");

        telemetry.runStarted(runId, "genetic");
        telemetry.candidatesGenerated(runId, 125);

        assertThat(registry.get("crypto.search.runs.active").gauge().value()).isEqualTo(1);
        assertThat(registry.get("crypto.candidates.generated").counter().count()).isEqualTo(125);

        telemetry.runFinished(runId, SearchRunStatus.COMPLETED);

        assertThat(registry.get("crypto.search.runs.active").gauge().value()).isZero();
        assertThat(registry.get("crypto.search.runs.finished")
                        .tag("status", "completed")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
