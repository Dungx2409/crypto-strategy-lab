package com.cryptolab.news.application;

import com.cryptolab.news.domain.SentimentLabel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class SentimentEvaluation {

    private SentimentEvaluation() {}

    public static BigDecimal macroF1(
            List<SentimentLabel> expected, List<SentimentLabel> predicted) {
        if (expected.isEmpty() || expected.size() != predicted.size()) {
            throw new IllegalArgumentException("expected and predicted labels must have the same non-zero size");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SentimentLabel label : SentimentLabel.values()) {
            long truePositive = 0;
            long falsePositive = 0;
            long falseNegative = 0;
            for (int index = 0; index < expected.size(); index++) {
                boolean actual = expected.get(index) == label;
                boolean prediction = predicted.get(index) == label;
                if (actual && prediction) truePositive++;
                else if (!actual && prediction) falsePositive++;
                else if (actual) falseNegative++;
            }
            long denominator = 2 * truePositive + falsePositive + falseNegative;
            BigDecimal f1 = denominator == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(2 * truePositive)
                            .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
            total = total.add(f1);
        }
        return total.divide(
                BigDecimal.valueOf(SentimentLabel.values().length), 4, RoundingMode.HALF_UP);
    }
}
