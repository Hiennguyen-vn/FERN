-- Add cogs_amount to sales_fact so COGS can be tracked per sale line.
-- COGS is populated asynchronously from SALE_USAGE inventory events ingested
-- into inventory_movement_fact (source_reference_type = 'SALE_ORDER').
ALTER TABLE report.sales_fact
    ADD COLUMN IF NOT EXISTS cogs_amount NUMERIC(18, 2) NOT NULL DEFAULT 0;

-- Index to support COGS JOIN: sales_fact ← inventory_movement_fact
-- WHERE source_reference_type = 'SALE_ORDER' AND source_reference_id = sale_order_id
CREATE INDEX IF NOT EXISTS idx_inventory_movement_fact_sale_order
    ON report.inventory_movement_fact (source_reference_type, source_reference_id)
    WHERE source_reference_type = 'SALE_ORDER';

-- Index to support daily COGS aggregation by outlet + business_date
CREATE INDEX IF NOT EXISTS idx_inventory_movement_fact_outlet_date
    ON report.inventory_movement_fact (outlet_id, business_date, movement_type);
