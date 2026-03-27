ALTER TABLE procurement_master.supplier
    ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS activated_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ;
