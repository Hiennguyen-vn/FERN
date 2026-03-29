ALTER TABLE pos.outbox_event
    DROP CONSTRAINT IF EXISTS pos_outbox_status_ck;

ALTER TABLE pos.outbox_event
    ADD CONSTRAINT pos_outbox_status_ck
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
