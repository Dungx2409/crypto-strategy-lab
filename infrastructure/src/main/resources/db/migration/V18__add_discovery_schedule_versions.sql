CREATE TABLE discovery_schedule_versions (
    schedule_id UUID NOT NULL REFERENCES discovery_schedules(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    symbol VARCHAR(30) NOT NULL,
    timeframe VARCHAR(10) NOT NULL,
    lookback_seconds BIGINT NOT NULL CHECK (lookback_seconds > 0),
    initial_capital NUMERIC(30, 12) NOT NULL CHECK (initial_capital > 0),
    candidate_limit BIGINT NOT NULL CHECK (candidate_limit > 0),
    interval_seconds BIGINT NOT NULL CHECK (interval_seconds >= 60),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (schedule_id, version)
);

INSERT INTO discovery_schedule_versions (
    schedule_id, version, symbol, timeframe, lookback_seconds,
    initial_capital, candidate_limit, interval_seconds, created_at
)
SELECT id, 1, symbol, timeframe, lookback_seconds, initial_capital,
       candidate_limit, interval_seconds, created_at
FROM discovery_schedules;
