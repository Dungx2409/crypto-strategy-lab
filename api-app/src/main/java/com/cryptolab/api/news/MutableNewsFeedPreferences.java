package com.cryptolab.api.news;

import com.cryptolab.news.port.NewsFeedPreferences;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class MutableNewsFeedPreferences implements NewsFeedPreferences {

    static final Set<String> ALLOWED_INTERVALS = Set.of("off", "1m", "2m", "5m", "1h");
    private static final Map<String, String> COIN_ALIASES = coinAliases();

    private volatile String interval = "5m";
    private volatile String coin = "ALL";
    private volatile String provider;

    MutableNewsFeedPreferences() {
        this("all");
    }

    @Autowired
    public MutableNewsFeedPreferences(
            @Value("${crypto.news.provider:all}") String provider) {
        this.provider = normalizeProvider(provider);
    }

    @Override
    public String categoriesCsv() {
        String selected = coin;
        if (selected == null || selected.isBlank() || "ALL".equalsIgnoreCase(selected)) {
            return "";
        }
        return selected;
    }

    @Override
    public String providerCode() {
        return provider;
    }

    public synchronized NewsPreferencesResponse snapshot() {
        return new NewsPreferencesResponse(
                interval, coin, categoriesCsv(), provider,
                java.util.List.of("CRYPTOCOMPARE", "RSS", "ALL"));
    }

    public synchronized NewsPreferencesResponse update(
            String intervalCode, String coinOrPair, String providerCode) {
        this.interval = normalizeInterval(intervalCode);
        this.coin = normalizeCoin(coinOrPair);
        this.provider = normalizeProvider(providerCode);
        return snapshot();
    }

    public boolean autoCrawlEnabled() {
        return !"off".equals(interval);
    }

    /**
     * Scheduled crawl delay, or {@code null} when auto-crawl is off.
     */
    public Duration intervalDuration() {
        return switch (interval) {
            case "off" -> null;
            case "1m" -> Duration.ofMinutes(1);
            case "2m" -> Duration.ofMinutes(2);
            case "1h" -> Duration.ofHours(1);
            default -> Duration.ofMinutes(5);
        };
    }

    public String intervalCode() {
        return interval;
    }

    public String coinCode() {
        return coin;
    }

    private static String normalizeInterval(String intervalCode) {
        if (intervalCode == null || intervalCode.isBlank()) {
            return "5m";
        }
        String value = intervalCode.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_INTERVALS.contains(value)) {
            throw new IllegalArgumentException(
                    "interval must be one of " + ALLOWED_INTERVALS);
        }
        return value;
    }

    private static String normalizeCoin(String coinOrPair) {
        if (coinOrPair == null || coinOrPair.isBlank() || "ALL".equalsIgnoreCase(coinOrPair.trim())) {
            return "ALL";
        }
        String raw = coinOrPair.trim().toUpperCase(Locale.ROOT);
        String mapped = COIN_ALIASES.getOrDefault(raw, raw);
        if (!mapped.matches("[A-Z0-9]{2,10}(,[A-Z0-9]{2,10}){0,4}")) {
            throw new IllegalArgumentException(
                    "coin must be ALL or a CryptoCompare category like BTC, ETH, SOL");
        }
        return mapped;
    }

    private static String normalizeProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) return "ALL";
        return switch (providerCode.trim().toUpperCase(Locale.ROOT)) {
            case "CRYPTOCOMPARE" -> "CRYPTOCOMPARE";
            case "RSS" -> "RSS";
            case "ALL", "COMPOSITE" -> "ALL";
            default -> throw new IllegalArgumentException(
                    "provider must be CryptoCompare, RSS, or All providers");
        };
    }

    private static Map<String, String> coinAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("BTCUSDT", "BTC");
        aliases.put("ETHUSDT", "ETH");
        aliases.put("SOLUSDT", "SOL");
        aliases.put("BNBUSDT", "BNB");
        aliases.put("XRPUSDT", "XRP");
        aliases.put("ADAUSDT", "ADA");
        aliases.put("DOGEUSDT", "DOGE");
        return Map.copyOf(aliases);
    }
}
