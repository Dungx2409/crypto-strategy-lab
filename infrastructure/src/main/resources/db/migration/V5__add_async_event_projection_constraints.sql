ALTER TABLE leaderboard_entries
    ADD CONSTRAINT leaderboard_search_rank_unique UNIQUE (search_run_id, rank);

CREATE INDEX idx_processed_events_event_id ON processed_events (event_id);
