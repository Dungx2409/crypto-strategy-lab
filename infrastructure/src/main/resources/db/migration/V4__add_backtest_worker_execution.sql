ALTER TABLE backtest_jobs
    DROP CONSTRAINT backtest_job_status_valid,
    DROP CONSTRAINT backtest_job_queued_time_consistent;

ALTER TABLE backtest_jobs
    ADD COLUMN retry_count integer NOT NULL DEFAULT 0,
    ADD COLUMN execution_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN worker_id varchar(128),
    ADD COLUMN lease_until timestamptz,
    ADD COLUMN started_at timestamptz,
    ADD COLUMN completed_at timestamptz;

ALTER TABLE backtest_jobs
    ADD CONSTRAINT backtest_job_status_valid CHECK (
        status IN (
            'PENDING_DISPATCH', 'QUEUED', 'RUNNING', 'RETRY_PENDING',
            'COMPLETED', 'CANCELLED', 'FAILED'
        )
    ),
    ADD CONSTRAINT backtest_job_retry_count_valid CHECK (retry_count BETWEEN 0 AND 3),
    ADD CONSTRAINT backtest_job_execution_attempts_nonnegative CHECK (execution_attempts >= 0),
    ADD CONSTRAINT backtest_job_claim_consistent CHECK (
        (status = 'RUNNING' AND worker_id IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND worker_id IS NULL AND lease_until IS NULL)
    ),
    ADD CONSTRAINT backtest_job_completed_time_consistent CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    ADD CONSTRAINT backtest_job_queued_requires_confirmation CHECK (
        status <> 'QUEUED' OR queued_at IS NOT NULL
    );

CREATE INDEX idx_backtest_jobs_expired_lease
    ON backtest_jobs (lease_until)
    WHERE status = 'RUNNING';
