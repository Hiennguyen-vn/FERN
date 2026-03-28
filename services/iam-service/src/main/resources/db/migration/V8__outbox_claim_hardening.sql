ALTER TABLE iam.outbox_event
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error TEXT;

ALTER TABLE iam.outbox_event
    DROP CONSTRAINT IF EXISTS iam_outbox_status_ck;

ALTER TABLE iam.outbox_event
    ADD CONSTRAINT iam_outbox_status_ck
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
