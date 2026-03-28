ALTER TABLE procurement.outbox_event
    DROP CONSTRAINT IF EXISTS procurement_outbox_status_ck;

ALTER TABLE procurement.outbox_event
    ADD CONSTRAINT procurement_outbox_status_ck
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
