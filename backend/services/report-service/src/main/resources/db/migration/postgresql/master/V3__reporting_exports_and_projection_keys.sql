ALTER TABLE report.sales_fact
    ADD COLUMN IF NOT EXISTS line_number INTEGER;

ALTER TABLE report.inventory_movement_fact
    ADD COLUMN IF NOT EXISTS source_reference_type VARCHAR,
    ADD COLUMN IF NOT EXISTS source_reference_id VARCHAR;

ALTER TABLE report.procurement_fact
    ADD COLUMN IF NOT EXISTS fact_type VARCHAR,
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR,
    ADD COLUMN IF NOT EXISTS reference_id VARCHAR;

ALTER TABLE report.export_job
    ADD COLUMN IF NOT EXISTS requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS row_count BIGINT,
    ADD COLUMN IF NOT EXISTS preview_payload JSONB,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(128);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.sales_fact'::regclass
          AND conname = 'uk_sales_fact_source_event'
    ) THEN
        ALTER TABLE report.sales_fact DROP CONSTRAINT uk_sales_fact_source_event;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.payment_fact'::regclass
          AND conname = 'uk_payment_fact_source_event'
    ) THEN
        ALTER TABLE report.payment_fact DROP CONSTRAINT uk_payment_fact_source_event;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.inventory_movement_fact'::regclass
          AND conname = 'uk_inventory_movement_fact_source_event'
    ) THEN
        ALTER TABLE report.inventory_movement_fact DROP CONSTRAINT uk_inventory_movement_fact_source_event;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.region_daily_summary'::regclass
          AND conname = 'uk_region_daily_summary_source_event'
    ) THEN
        ALTER TABLE report.region_daily_summary DROP CONSTRAINT uk_region_daily_summary_source_event;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.region_daily_summary'::regclass
          AND conname = 'uk_region_daily_summary_idempotency'
    ) THEN
        ALTER TABLE report.region_daily_summary DROP CONSTRAINT uk_region_daily_summary_idempotency;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.company_daily_summary'::regclass
          AND conname = 'uk_company_daily_summary_source_event'
    ) THEN
        ALTER TABLE report.company_daily_summary DROP CONSTRAINT uk_company_daily_summary_source_event;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.company_daily_summary'::regclass
          AND conname = 'uk_company_daily_summary_idempotency'
    ) THEN
        ALTER TABLE report.company_daily_summary DROP CONSTRAINT uk_company_daily_summary_idempotency;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_sales_fact_source_key
    ON report.sales_fact (source_event_id, line_number);

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_payment_fact_source_key
    ON report.payment_fact (source_event_id, payment_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_inventory_movement_fact_source_key
    ON report.inventory_movement_fact (source_event_id, ingredient_id, movement_type, source_reference_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_region_daily_summary_key
    ON report.region_daily_summary (region_id, business_date);

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_company_daily_summary_key
    ON report.company_daily_summary (business_date);
