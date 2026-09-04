package com.cryptolab.api.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MutableNewsFeedPreferencesTest {

    @Test
    void mapsPairsAndIntervalsForRuntimeCollection() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();

        var updated = preferences.update("1m", "BTCUSDT", "RSS");

        assertThat(updated.interval()).isEqualTo("1m");
        assertThat(updated.coin()).isEqualTo("BTC");
        assertThat(updated.categories()).isEqualTo("BTC");
        assertThat(preferences.intervalDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(preferences.categoriesCsv()).isEqualTo("BTC");
        assertThat(updated.provider()).isEqualTo("RSS");
    }

    @Test
    void allCoinsClearsCategoryFilter() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();
        preferences.update("2m", "ETH", "CRYPTOCOMPARE");

        var updated = preferences.update("1h", "ALL", "ALL");

        assertThat(updated.coin()).isEqualTo("ALL");
        assertThat(updated.categories()).isBlank();
        assertThat(preferences.intervalDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(updated.availableProviders()).containsExactly(
                "CRYPTOCOMPARE", "RSS", "ALL");
    }

    @Test
    void rejectsUnknownInterval() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();
        assertThatThrownBy(() -> preferences.update("3m", "BTC", "ALL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void offDisablesAutoCrawlSchedule() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences();

        var updated = preferences.update("off", "BTC", "ALL");

        assertThat(updated.interval()).isEqualTo("off");
        assertThat(preferences.autoCrawlEnabled()).isFalse();
        assertThat(preferences.intervalDuration()).isNull();
    }

    @Test
    void acceptsCompositeAsTheAllProvidersAlias() {
        MutableNewsFeedPreferences preferences = new MutableNewsFeedPreferences("composite");

        assertThat(preferences.providerCode()).isEqualTo("ALL");
    }
}
