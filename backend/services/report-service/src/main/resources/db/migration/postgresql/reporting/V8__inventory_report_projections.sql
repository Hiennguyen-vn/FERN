CREATE TABLE IF NOT EXISTS report.inventory_stock_snapshot (
    snapshot_id BIGINT PRIMARY KEY,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    qty_on_hand NUMERIC(18, 4) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(18, 2),
    last_count_date DATE,
    last_movement_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_stock_snapshot_outlet_ingredient UNIQUE (outlet_id, ingredient_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_stock_snapshot_outlet
    ON report.inventory_stock_snapshot (outlet_id, ingredient_id);

CREATE INDEX IF NOT EXISTS idx_inventory_stock_snapshot_region
    ON report.inventory_stock_snapshot (region_id, outlet_id);

CREATE TABLE IF NOT EXISTS report.projection_watermark (
    dataset VARCHAR PRIMARY KEY,
    last_occurred_at TIMESTAMPTZ,
    last_ingested_at TIMESTAMPTZ,
    failed_landing_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
