ALTER TABLE org.outbox_event
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error TEXT;

ALTER TABLE org.outbox_event
    DROP CONSTRAINT IF EXISTS org_outbox_status_ck;

ALTER TABLE org.outbox_event
    ADD CONSTRAINT org_outbox_status_ck
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
