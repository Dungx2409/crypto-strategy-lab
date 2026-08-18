ALTER TABLE outbox_events
    ADD COLUMN cancelled_at timestamptz;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_terminal_delivery_state CHECK (
        published_at IS NULL OR cancelled_at IS NULL
    );

CREATE INDEX idx_outbox_cancelled ON outbox_events (cancelled_at)
    WHERE cancelled_at IS NOT NULL;
