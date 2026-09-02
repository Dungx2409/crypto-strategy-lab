package com.cryptolab.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptolab.experiment.domain.LeaderboardSort;
import com.cryptolab.experiment.domain.SortDirection;
import org.junit.jupiter.api.Test;

class LeaderboardSortTest {

    @Test
    void parsesSortAliasesUsedByTheDashboardApi() {
        assertThat(LeaderboardSort.parse(null)).isEqualTo(LeaderboardSort.SCORE);
        assertThat(LeaderboardSort.parse("return_pct")).isEqualTo(LeaderboardSort.RETURN);
        assertThat(LeaderboardSort.parse("win-rate")).isEqualTo(LeaderboardSort.WIN_RATE);
        assertThat(LeaderboardSort.parse("mdd")).isEqualTo(LeaderboardSort.MAX_DRAWDOWN);
        assertThat(LeaderboardSort.parse("total_trades")).isEqualTo(LeaderboardSort.TRADES);
        assertThat(SortDirection.parse(null)).isEqualTo(SortDirection.DESC);
        assertThat(SortDirection.parse("ascending")).isEqualTo(SortDirection.ASC);
    }

    @Test
    void rejectsUnknownSortInput() {
        assertThatThrownBy(() -> LeaderboardSort.parse("profit_factor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported leaderboard sort");
        assertThatThrownBy(() -> SortDirection.parse("sideways"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported sort direction");
    }
}
