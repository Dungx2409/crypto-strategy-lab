package com.cryptolab.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.application.SentimentEvaluation;
import com.cryptolab.news.domain.SentimentLabel;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentimentEvaluationTest {

    @Test
    void calculatesMacroF1AcrossAllThreeLabels() {
        var expected = List.of(
                SentimentLabel.POSITIVE, SentimentLabel.POSITIVE,
                SentimentLabel.NEUTRAL, SentimentLabel.NEUTRAL,
                SentimentLabel.NEGATIVE, SentimentLabel.NEGATIVE);
        var predicted = List.of(
                SentimentLabel.POSITIVE, SentimentLabel.NEUTRAL,
                SentimentLabel.NEUTRAL, SentimentLabel.NEUTRAL,
                SentimentLabel.NEGATIVE, SentimentLabel.POSITIVE);

        assertThat(SentimentEvaluation.macroF1(expected, predicted))
                .isEqualByComparingTo("0.6556");
    }
}
