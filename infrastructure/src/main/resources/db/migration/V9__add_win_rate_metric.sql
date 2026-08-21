ALTER TABLE evaluation_metrics
    ADD COLUMN win_rate_pct numeric(38, 18);

UPDATE evaluation_metrics metrics
SET win_rate_pct = COALESCE(
    (SELECT 100.0 * COUNT(*) FILTER (WHERE trades.pnl > 0) / NULLIF(COUNT(*), 0)
     FROM trades
     WHERE trades.experiment_id = metrics.experiment_id),
    0
);

ALTER TABLE evaluation_metrics
    ALTER COLUMN win_rate_pct SET DEFAULT 0,
    ALTER COLUMN win_rate_pct SET NOT NULL,
    ADD CONSTRAINT evaluation_metrics_win_rate_bounded
        CHECK (win_rate_pct >= 0 AND win_rate_pct <= 100);

ALTER TABLE leaderboard_entries
    ADD COLUMN win_rate_pct numeric(38, 18);

UPDATE leaderboard_entries leaderboard
SET win_rate_pct = metrics.win_rate_pct
FROM evaluation_metrics metrics
WHERE metrics.experiment_id = leaderboard.experiment_id;

UPDATE leaderboard_entries
SET win_rate_pct = 0
WHERE win_rate_pct IS NULL;

ALTER TABLE leaderboard_entries
    ALTER COLUMN win_rate_pct SET DEFAULT 0,
    ALTER COLUMN win_rate_pct SET NOT NULL,
    ADD CONSTRAINT leaderboard_win_rate_bounded
        CHECK (win_rate_pct >= 0 AND win_rate_pct <= 100);
