-- Align purge index with the outbox status state machine (PUBLISHED, not SENT).
DROP INDEX IF EXISTS pos.idx_outbox_event_sent_created;

CREATE INDEX IF NOT EXISTS idx_outbox_event_published_created
    ON pos.outbox_event (created_at)
    WHERE status = 'PUBLISHED';
