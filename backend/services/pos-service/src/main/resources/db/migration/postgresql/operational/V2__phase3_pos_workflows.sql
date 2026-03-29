ALTER TABLE pos.pos_session
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expected_cash_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS counted_cash_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS discrepancy_amount NUMERIC(18, 2);

ALTER TABLE pos.sale_order
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS reservation_id BIGINT;

ALTER TABLE pos.sale_payment
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pos_sale_payment_idempotency_key
    ON pos.sale_payment (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pos_single_open_session_per_outlet
    ON pos.pos_session (outlet_id)
    WHERE status = 'OPEN';
