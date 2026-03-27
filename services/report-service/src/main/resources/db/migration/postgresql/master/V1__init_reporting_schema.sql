CREATE SCHEMA IF NOT EXISTS raw_events;
CREATE SCHEMA IF NOT EXISTS report;

CREATE TABLE IF NOT EXISTS raw_events.event_landing (
    landing_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    partition_key VARCHAR,
    kafka_topic VARCHAR,
    kafka_partition BIGINT,
    kafka_offset BIGINT,
    payload JSONB NOT NULL,
    CONSTRAINT uk_event_landing_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_event_landing_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_event_landing_occurred_at ON raw_events.event_landing (occurred_at DESC, landing_id DESC);

CREATE TABLE IF NOT EXISTS report.sales_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    sale_order_id BIGINT,
    product_id BIGINT,
    business_date DATE NOT NULL,
    qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    gross_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_sales_fact_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_sales_fact_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.payment_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    sale_order_id BIGINT,
    payment_id BIGINT,
    business_date DATE NOT NULL,
    payment_method VARCHAR,
    payment_status VARCHAR,
    amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_payment_fact_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_payment_fact_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.inventory_movement_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    ingredient_id BIGINT,
    business_date DATE NOT NULL,
    movement_type VARCHAR NOT NULL,
    qty_change NUMERIC(18, 4) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(18, 2),
    payload JSONB,
    CONSTRAINT uk_inventory_movement_fact_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_inventory_movement_fact_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.procurement_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    supplier_id BIGINT,
    purchase_order_id BIGINT,
    goods_receipt_id BIGINT,
    business_date DATE NOT NULL,
    fact_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_procurement_fact_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_procurement_fact_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.attendance_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    employee_id BIGINT,
    business_date DATE NOT NULL,
    attendance_status VARCHAR,
    work_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    overtime_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_attendance_fact_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_attendance_fact_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.payroll_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    employee_id BIGINT,
    payroll_run_id BIGINT,
    business_date DATE NOT NULL,
    gross_pay NUMERIC(18, 2) NOT NULL DEFAULT 0,
    net_pay NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_payroll_fact_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_payroll_fact_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.expense_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    expense_record_id BIGINT,
    business_date DATE NOT NULL,
    source_type VARCHAR,
    amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_expense_fact_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_expense_fact_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.region_daily_summary (
    summary_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    total_sales NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_procurement NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_expense NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_payroll NUMERIC(18, 2) NOT NULL DEFAULT 0,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_region_daily_summary_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_region_daily_summary_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.company_daily_summary (
    summary_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    business_date DATE NOT NULL,
    total_sales NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_procurement NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_expense NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_payroll NUMERIC(18, 2) NOT NULL DEFAULT 0,
    outlet_count BIGINT NOT NULL DEFAULT 0,
    payload JSONB,
    CONSTRAINT uk_company_daily_summary_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_company_daily_summary_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report.export_job (
    export_job_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR,
    source_service VARCHAR,
    event_type VARCHAR,
    occurred_at TIMESTAMPTZ,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR,
    report_type VARCHAR NOT NULL,
    format VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    requested_by VARCHAR,
    file_path VARCHAR,
    completed_at TIMESTAMPTZ,
    payload JSONB,
    CONSTRAINT uk_export_job_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_export_job_idempotency UNIQUE (idempotency_key)
);
