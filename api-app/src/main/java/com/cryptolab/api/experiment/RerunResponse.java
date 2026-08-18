package com.cryptolab.api.experiment;

import com.cryptolab.experiment.domain.RerunResult;
import java.util.UUID;

public record RerunResponse(
        UUID sourceExperimentId,
        boolean metricsMatch,
        ExperimentDetailsResponse experiment) {

    static RerunResponse from(RerunResult result) {
        return new RerunResponse(
                result.sourceExperimentId(),
                result.metricsMatch(),
                ExperimentDetailsResponse.from(result.reproducedExperiment()));
    }
}
