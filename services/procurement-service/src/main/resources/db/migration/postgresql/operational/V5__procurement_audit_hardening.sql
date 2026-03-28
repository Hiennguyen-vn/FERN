ALTER TABLE procurement.purchase_order
    ADD COLUMN IF NOT EXISTS updated_by_user_id BIGINT;

ALTER TABLE procurement.goods_receipt
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

ALTER TABLE procurement.supplier_invoice
    ADD COLUMN IF NOT EXISTS disputed_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS disputed_at TIMESTAMPTZ;
