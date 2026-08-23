package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.shared.domain.SentimentObservation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class MarketDatasetChecksum {

    private MarketDatasetChecksum() {}

    public static String calculate(List<Candle> candles) {
        return calculate(candles, List.of());
    }

    public static String calculate(
            List<Candle> candles,
            List<SentimentObservation> sentimentObservations) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("candles must not be empty");
        }
        if (sentimentObservations == null) {
            throw new IllegalArgumentException("sentimentObservations must not be null");
        }
        StringBuilder canonical = new StringBuilder();
        for (int index = 0; index < candles.size(); index++) {
            Candle candle = candles.get(index);
            canonical.append(index).append('|')
                    .append(candle.symbol()).append('|')
                    .append(candle.timeframe().exchangeCode()).append('|')
                    .append(candle.openTime()).append('|')
                    .append(decimal(candle.open())).append('|')
                    .append(decimal(candle.high())).append('|')
                    .append(decimal(candle.low())).append('|')
                    .append(decimal(candle.close())).append('|')
                    .append(decimal(candle.volume())).append('\n');
        }
        if (!sentimentObservations.isEmpty()) {
            canonical.append("sentiment\n");
            for (int index = 0; index < sentimentObservations.size(); index++) {
                SentimentObservation observation = sentimentObservations.get(index);
                canonical.append(index).append('|')
                        .append(observation.sourceId()).append('|')
                        .append(observation.observedAt()).append('|')
                        .append(decimal(observation.score())).append('|')
                        .append(observation.modelName()).append('|')
                        .append(observation.modelVersion()).append('|')
                        .append(observation.inputVersion()).append('|')
                        .append(observation.preprocessingVersion()).append('\n');
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String decimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }
}
