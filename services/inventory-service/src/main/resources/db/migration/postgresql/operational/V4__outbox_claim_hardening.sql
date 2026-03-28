ALTER TABLE inventory.outbox_event
    DROP CONSTRAINT IF EXISTS inventory_outbox_status_ck;

ALTER TABLE inventory.outbox_event
    ADD CONSTRAINT inventory_outbox_status_ck
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
