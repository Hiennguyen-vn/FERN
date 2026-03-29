ALTER TABLE raw_events.event_landing
    ADD COLUMN IF NOT EXISTS status VARCHAR NOT NULL DEFAULT 'PROCESSED',
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS error_message VARCHAR;

UPDATE raw_events.event_landing
SET status = 'PROCESSED',
    processed_at = COALESCE(processed_at, ingested_at)
WHERE status <> 'PROCESSED'
   OR processed_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'raw_events.event_landing'::regclass
          AND conname = 'event_landing_status_ck'
    ) THEN
        ALTER TABLE raw_events.event_landing
            ADD CONSTRAINT event_landing_status_ck CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'));
    END IF;
END $$;
