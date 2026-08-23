CREATE TABLE experiment_owners (
    experiment_id UUID PRIMARY KEY REFERENCES experiments(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_experiment_owners_account
    ON experiment_owners(account_id, created_at DESC);
