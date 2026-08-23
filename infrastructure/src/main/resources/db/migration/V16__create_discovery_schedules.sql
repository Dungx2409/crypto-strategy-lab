CREATE TABLE discovery_schedules (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol VARCHAR(30) NOT NULL,
    timeframe VARCHAR(10) NOT NULL,
    lookback_seconds BIGINT NOT NULL CHECK (lookback_seconds > 0),
    initial_capital NUMERIC(30, 12) NOT NULL CHECK (initial_capital > 0),
    candidate_limit BIGINT NOT NULL CHECK (candidate_limit > 0),
    interval_seconds BIGINT NOT NULL CHECK (interval_seconds >= 60),
    status VARCHAR(20) NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    active_search_run_id UUID,
    completed_runs BIGINT NOT NULL DEFAULT 0 CHECK (completed_runs >= 0),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_discovery_schedules_due
    ON discovery_schedules(status, next_run_at)
    WHERE active_search_run_id IS NULL;

CREATE INDEX idx_discovery_schedules_account
    ON discovery_schedules(account_id, created_at DESC);
