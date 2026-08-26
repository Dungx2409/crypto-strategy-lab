UPDATE search_runs
SET status = 'CANCELLED',
    cancel_requested = true,
    ended_at = COALESCE(ended_at, CURRENT_TIMESTAMP),
    stop_reason = COALESCE(stop_reason, 'USER_CANCELLED')
WHERE status = 'PAUSED';
