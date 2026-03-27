ALTER TABLE procurement.purchase_order
    ADD COLUMN IF NOT EXISTS issued_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS commercial_snapshot JSONB;

ALTER TABLE procurement.goods_receipt
    ADD COLUMN IF NOT EXISTS received_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS posted_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS posted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS posted_idempotency_key VARCHAR(150);

ALTER TABLE procurement.supplier_invoice
    ADD COLUMN IF NOT EXISTS region_id BIGINT,
    ADD COLUMN IF NOT EXISTS outlet_id BIGINT;

ALTER TABLE procurement.supplier_payment
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS uq_procurement_goods_receipt_post_idempotency
    ON procurement.goods_receipt (posted_idempotency_key)
    WHERE posted_idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_procurement_supplier_payment_idempotency
    ON procurement.supplier_payment (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
