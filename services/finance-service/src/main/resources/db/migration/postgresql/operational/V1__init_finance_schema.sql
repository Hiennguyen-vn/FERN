CREATE SCHEMA IF NOT EXISTS finance;

CREATE TABLE IF NOT EXISTS finance.payroll_period (
    id BIGSERIAL PRIMARY KEY,
    region_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    pay_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT finance_payroll_period_uk UNIQUE (region_id, start_date, end_date),
    CONSTRAINT finance_payroll_period_status_ck CHECK (status IN ('DRAFT', 'CONFIRMED', 'PAID', 'CANCELLED')),
    CONSTRAINT finance_payroll_period_dates_ck CHECK (end_date >= start_date AND (pay_date IS NULL OR pay_date >= end_date))
);

CREATE TABLE IF NOT EXISTS finance.payroll_run (
    id BIGSERIAL PRIMARY KEY,
    payroll_period_id BIGINT NOT NULL REFERENCES finance.payroll_period(id) ON DELETE CASCADE,
    run_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    processed_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMPTZ,
    payment_ref VARCHAR(100),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT finance_payroll_run_status_ck CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS finance.payroll_employee_result (
    id BIGSERIAL PRIMARY KEY,
    payroll_run_id BIGINT NOT NULL REFERENCES finance.payroll_run(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL,
    outlet_id BIGINT,
    gross_pay NUMERIC(18, 2) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    net_pay NUMERIC(18, 2) NOT NULL DEFAULT 0,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    work_days NUMERIC(10, 2) NOT NULL DEFAULT 0,
    work_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    overtime_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT finance_payroll_employee_result_uk UNIQUE (payroll_run_id, employee_id),
    CONSTRAINT finance_payroll_employee_result_amount_ck CHECK (
        gross_pay >= 0 AND deduction_amount >= 0 AND tax_amount >= 0 AND net_pay >= 0
    ),
    CONSTRAINT finance_payroll_employee_result_status_ck CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'REFUNDED'))
);

CREATE TABLE IF NOT EXISTS finance.expense_record (
    id BIGSERIAL PRIMARY KEY,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    employee_id BIGINT,
    expense_time TIMESTAMPTZ NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    submitted_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT finance_expense_record_amount_ck CHECK (amount >= 0),
    CONSTRAINT finance_expense_record_source_ck CHECK (source_type IN ('INVENTORY_PURCHASE', 'OPERATING_EXPENSE', 'PAYROLL')),
    CONSTRAINT finance_expense_record_status_ck CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS finance.expense_inventory_purchase (
    expense_record_id BIGINT PRIMARY KEY REFERENCES finance.expense_record(id) ON DELETE CASCADE,
    goods_receipt_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS finance.expense_operating (
    expense_record_id BIGINT PRIMARY KEY REFERENCES finance.expense_record(id) ON DELETE CASCADE,
    expense_type VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT finance_expense_operating_type_ck CHECK (expense_type IN ('FIXED', 'VARIABLE', 'ONE_TIME'))
);

CREATE TABLE IF NOT EXISTS finance.expense_other (
    expense_record_id BIGINT PRIMARY KEY REFERENCES finance.expense_record(id) ON DELETE CASCADE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS finance.expense_payroll (
    expense_record_id BIGINT PRIMARY KEY REFERENCES finance.expense_record(id) ON DELETE CASCADE,
    payroll_run_id BIGINT NOT NULL REFERENCES finance.payroll_run(id)
);

CREATE TABLE IF NOT EXISTS finance.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT finance_outbox_status_ck CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_finance_payroll_period_route ON finance.payroll_period (region_id, start_date);
CREATE INDEX IF NOT EXISTS idx_finance_payroll_run_status ON finance.payroll_run (status, run_date);
CREATE INDEX IF NOT EXISTS idx_finance_expense_route ON finance.expense_record (region_id, outlet_id, expense_time);
