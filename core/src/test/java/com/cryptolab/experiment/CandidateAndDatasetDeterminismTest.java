package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.MarketDataset;
import com.cryptolab.experiment.domain.MarketDatasetChecksum;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CandidateAndDatasetDeterminismTest {

    @Test
    void candidateHashIsIndependentOfMapAndStrategyOrdering() {
        Map<String, Object> firstParameters = new LinkedHashMap<>();
        firstParameters.put("fastPeriod", 10);
        firstParameters.put("slowPeriod", new BigDecimal("20.0"));
        Map<String, Object> reorderedParameters = new LinkedHashMap<>();
        reorderedParameters.put("slowPeriod", 20);
        reorderedParameters.put("fastPeriod", new BigDecimal("10.00"));
        StrategyDefinition ma1 = new StrategyDefinition("MA", "1.0", firstParameters);
        StrategyDefinition ma2 = new StrategyDefinition("MA", "1.0", reorderedParameters);
        StrategyDefinition rsi = new StrategyDefinition("RSI", "1.0", Map.of("period", 14));
        CombinationPolicyDefinition policy = new CombinationPolicyDefinition(
                "WEIGHTED", "1.0", Map.of("MA", new BigDecimal("0.5"), "RSI", new BigDecimal("0.50")),
                new BigDecimal("0.10"));

        assertThat(CandidateCanonicalizer.hash(List.of(ma1, rsi), policy))
                .isEqualTo(CandidateCanonicalizer.hash(List.of(rsi, ma2), policy));
    }

    @Test
    void candidateVerificationRejectsTamperedHash() {
        CandidateStrategy valid = ExperimentTestFixtures.candidate();
        CandidateStrategy tampered = new CandidateStrategy(
                valid.candidateId(), valid.strategies(), valid.combinationPolicy(), "not-the-canonical-hash");

        assertThatThrownBy(() -> CandidateCanonicalizer.verify(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidateHash");
    }

    @Test
    void datasetChecksumChangesWithOrderedCandleContentAndIsVerified() {
        MarketDataset valid = ExperimentTestFixtures.dataset();
        String reordered = MarketDatasetChecksum.calculate(List.of(
                valid.candles().get(1), valid.candles().get(0), valid.candles().get(2)));

        assertThat(reordered).isNotEqualTo(valid.reference().checksum());
        MarketDatasetRef badReference = new MarketDatasetRef(
                valid.reference().symbol(),
                valid.reference().timeframe(),
                valid.reference().from(),
                valid.reference().to(),
                valid.reference().datasetVersion(),
                "bad-checksum");
        assertThatThrownBy(() -> new MarketDataset(valid.id(), badReference, valid.candles()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }
}
