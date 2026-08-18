package com.cryptolab.experiment.port;

import com.cryptolab.experiment.domain.CandidateStrategy;
import java.util.UUID;

public interface CandidateProvider {
    CandidateStrategy getCandidate(UUID candidateId);
}
