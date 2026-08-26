ALTER TABLE search_runs
    ADD COLUMN owner_account_id UUID REFERENCES accounts(id) ON DELETE CASCADE,
    ADD COLUMN run_kind VARCHAR(16) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE search_runs
    ADD CONSTRAINT ck_search_runs_kind
        CHECK (run_kind IN ('LEGACY', 'MANUAL', 'SEARCH', 'DISCOVERY')),
    ADD CONSTRAINT ck_search_runs_owner
        CHECK (run_kind = 'LEGACY' OR owner_account_id IS NOT NULL);

CREATE INDEX idx_search_runs_owner_created
    ON search_runs(owner_account_id, created_at DESC)
    WHERE owner_account_id IS NOT NULL;
