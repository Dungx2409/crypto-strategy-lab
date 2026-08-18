CREATE TABLE backtest_jobs (
    job_id uuid PRIMARY KEY,
    experiment_id uuid NOT NULL UNIQUE REFERENCES experiments(id),
    search_run_id uuid NOT NULL REFERENCES search_runs(id),
    outbox_event_id uuid NOT NULL UNIQUE REFERENCES outbox_events(event_id) DEFERRABLE INITIALLY DEFERRED,
    status varchar(32) NOT NULL,
    payload_json jsonb NOT NULL,
    dispatch_attempts integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    queued_at timestamptz,
    last_error text,
    CONSTRAINT backtest_job_status_valid CHECK (
        status IN ('PENDING_DISPATCH', 'QUEUED', 'CANCELLED', 'FAILED')
    ),
    CONSTRAINT backtest_job_attempts_nonnegative CHECK (dispatch_attempts >= 0),
    CONSTRAINT backtest_job_queued_time_consistent CHECK (
        (status = 'QUEUED' AND queued_at IS NOT NULL)
        OR (status <> 'QUEUED' AND queued_at IS NULL)
    )
);

ALTER TABLE outbox_events
    ADD COLUMN destination varchar(128),
    ADD COLUMN routing_key varchar(128),
    ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN claimed_by varchar(128),
    ADD COLUMN claimed_until timestamptz;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_claim_consistent CHECK (
        (claimed_by IS NULL AND claimed_until IS NULL)
        OR (claimed_by IS NOT NULL AND claimed_until IS NOT NULL)
    );

CREATE INDEX idx_backtest_jobs_search_status
    ON backtest_jobs (search_run_id, status);

CREATE INDEX idx_outbox_dispatch_ready
    ON outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL;
