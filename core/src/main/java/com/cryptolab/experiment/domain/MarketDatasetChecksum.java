package com.cryptolab.experiment.domain;

import com.cryptolab.marketdata.domain.Candle;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class MarketDatasetChecksum {

    private MarketDatasetChecksum() {}

    public static String calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("candles must not be empty");
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
