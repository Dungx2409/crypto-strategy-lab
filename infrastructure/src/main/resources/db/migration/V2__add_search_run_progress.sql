ALTER TABLE search_runs
    ADD COLUMN generated_candidates bigint NOT NULL DEFAULT 0,
    ADD COLUMN persisted_candidates bigint NOT NULL DEFAULT 0,
    ADD COLUMN best_score numeric(38, 18),
    ADD COLUMN no_improvement_iterations integer NOT NULL DEFAULT 0,
    ADD COLUMN stop_reason varchar(64),
    ADD COLUMN failure_code varchar(64),
    ADD COLUMN failure_message text;

ALTER TABLE search_runs
    ADD CONSTRAINT search_runs_generated_nonnegative CHECK (generated_candidates >= 0),
    ADD CONSTRAINT search_runs_persisted_bounds CHECK (
        persisted_candidates >= 0 AND persisted_candidates <= generated_candidates
    ),
    ADD CONSTRAINT search_runs_no_improvement_nonnegative CHECK (no_improvement_iterations >= 0);

CREATE INDEX idx_search_runs_status ON search_runs (status);
