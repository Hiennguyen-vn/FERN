-- L2: Index to support efficient outbox purge of old SENT events.
CREATE INDEX IF NOT EXISTS idx_outbox_event_sent_created
    ON pos.outbox_event (created_at)
    WHERE status = 'SENT';
