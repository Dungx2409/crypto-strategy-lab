package com.cryptolab.infrastructure.news.adapter;

import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.SentimentAnalyzer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class DeterministicKeywordSentimentAnalyzer implements SentimentAnalyzer {

    public static final ModelDescriptor MODEL =
            new ModelDescriptor("deterministic-keyword", "keyword-v1");
    public static final String PREPROCESSING_VERSION = "lowercase-token-v1";

    private static final Set<String> POSITIVE = Set.of(
            "adoption", "approve", "approved", "bullish", "gain", "gains", "growth",
            "high", "launch", "partnership", "rally", "record", "rise", "surge", "upgrade");
    private static final Set<String> NEGATIVE = Set.of(
            "ban", "bearish", "breach", "crash", "decline", "drop", "exploit", "fraud",
            "hack", "lawsuit", "loss", "losses", "low", "risk", "scam", "selloff");

    private final Clock clock;

    public DeterministicKeywordSentimentAnalyzer(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ModelDescriptor descriptor() {
        return MODEL;
    }

    @Override
    public String preprocessingVersion() {
        return PREPROCESSING_VERSION;
    }

    @Override
    public SentimentResult analyze(NewsItem item) {
        Objects.requireNonNull(item, "item must not be null");
        int positive = 0;
        int negative = 0;
        for (String token : item.normalizedText().toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (POSITIVE.contains(token)) {
                positive++;
            }
            if (NEGATIVE.contains(token)) {
                negative++;
            }
        }
        int recognized = positive + negative;
        BigDecimal score = recognized == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(positive - negative)
                        .divide(BigDecimal.valueOf(recognized), 4, RoundingMode.HALF_UP);
        SentimentLabel label = score.signum() > 0
                ? SentimentLabel.POSITIVE
                : score.signum() < 0 ? SentimentLabel.NEGATIVE : SentimentLabel.NEUTRAL;
        return new SentimentResult(
                item.newsId(),
                label,
                score,
                MODEL,
                item.inputVersion(),
                PREPROCESSING_VERSION,
                clock.instant());
    }
}
