package com.cryptolab.api.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MutableNewsFeedPreferencesTest {

    @Test
    void mapsPairsAndIntervalsForRuntimeCollection() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();

        var updated = preferences.update("1m", "BTCUSDT");

        assertThat(updated.interval()).isEqualTo("1m");
        assertThat(updated.coin()).isEqualTo("BTC");
        assertThat(updated.categories()).isEqualTo("BTC");
        assertThat(preferences.intervalDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(preferences.categoriesCsv()).isEqualTo("BTC");
    }

    @Test
    void allCoinsClearsCategoryFilter() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();
        preferences.update("2m", "ETH");

        var updated = preferences.update("1h", "ALL");

        assertThat(updated.coin()).isEqualTo("ALL");
        assertThat(updated.categories()).isBlank();
        assertThat(preferences.intervalDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void rejectsUnknownInterval() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();
        assertThatThrownBy(() -> preferences.update("3m", "BTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interval");
    }
}
