ALTER TABLE finance.outbox_event
    DROP CONSTRAINT IF EXISTS finance_outbox_status_ck;

ALTER TABLE finance.outbox_event
    ADD CONSTRAINT finance_outbox_status_ck
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
