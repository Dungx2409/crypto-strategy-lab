CREATE TABLE manual_run_batches (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    strategy_id UUID NOT NULL REFERENCES user_strategies(id) ON DELETE RESTRICT,
    symbol VARCHAR(32) NOT NULL,
    from_time TIMESTAMPTZ NOT NULL,
    to_time TIMESTAMPTZ NOT NULL,
    execution_config_json JSONB NOT NULL,
    status VARCHAR(24) NOT NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_manual_run_range CHECK (from_time < to_time),
    CONSTRAINT ck_manual_run_status CHECK (
        status IN ('PREPARING', 'RUNNING', 'COMPLETED', 'PARTIAL_FAILURE', 'FAILED', 'CANCELLED')
    )
);

CREATE TABLE manual_run_children (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES manual_run_batches(id) ON DELETE CASCADE,
    timeframe VARCHAR(8) NOT NULL,
    status VARCHAR(24) NOT NULL,
    experiment_id UUID REFERENCES experiments(id) ON DELETE SET NULL,
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(batch_id, timeframe),
    CONSTRAINT ck_manual_run_child_status CHECK (
        status IN ('PREPARING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX idx_manual_run_batches_account_created
    ON manual_run_batches(account_id, created_at DESC);
