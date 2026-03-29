CREATE TABLE IF NOT EXISTS catalog.promotion (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    promotion_type VARCHAR(30) NOT NULL,
    discount_percent NUMERIC(5, 2),
    discount_amount NUMERIC(18, 2),
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT,
    min_order_amount NUMERIC(18, 2),
    max_usage_total INT,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id BIGINT,
    updated_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_promotion_code_uk UNIQUE (code),
    CONSTRAINT catalog_promotion_discount_ck CHECK (
        ((discount_percent IS NOT NULL AND discount_amount IS NULL) OR (discount_percent IS NULL AND discount_amount IS NOT NULL))
        AND (discount_percent IS NULL OR (discount_percent >= 0 AND discount_percent <= 100))
        AND (discount_amount IS NULL OR discount_amount >= 0)
        AND (min_order_amount IS NULL OR min_order_amount >= 0)
        AND (max_usage_total IS NULL OR max_usage_total >= 0)
        AND (effective_to IS NULL OR effective_to >= effective_from)
    ),
    CONSTRAINT catalog_promotion_scope_ck CHECK (scope_type IN ('GLOBAL', 'REGION', 'OUTLET')),
    CONSTRAINT catalog_promotion_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT catalog_promotion_scope_id_ck CHECK (
        (scope_type = 'GLOBAL' AND scope_id IS NULL)
        OR (scope_type IN ('REGION', 'OUTLET') AND scope_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_catalog_promotion_lookup
    ON catalog.promotion (code, status, effective_from, effective_to);

CREATE INDEX IF NOT EXISTS idx_catalog_promotion_scope
    ON catalog.promotion (scope_type, scope_id, status);
