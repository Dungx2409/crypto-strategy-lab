ALTER TABLE discovery_schedules
ADD COLUMN last_search_run_id UUID;

UPDATE discovery_schedules
SET last_search_run_id = active_search_run_id
WHERE active_search_run_id IS NOT NULL;
