ALTER TABLE finance.payroll_period
    ADD COLUMN IF NOT EXISTS reference_code VARCHAR(50);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'finance.payroll_run'::regclass
          AND conname = 'finance_payroll_run_status_ck'
    ) THEN
        ALTER TABLE finance.payroll_run DROP CONSTRAINT finance_payroll_run_status_ck;
    END IF;
END $$;

ALTER TABLE finance.payroll_run
    ADD COLUMN IF NOT EXISTS run_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS submitted_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS paid_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

ALTER TABLE finance.payroll_run
    ADD CONSTRAINT finance_payroll_run_status_ck CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED'));

ALTER TABLE finance.payroll_employee_result
    ADD COLUMN IF NOT EXISTS contract_id BIGINT,
    ADD COLUMN IF NOT EXISTS exception_message TEXT;

ALTER TABLE finance.expense_record
    ADD COLUMN IF NOT EXISTS reference_code VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_payroll_run_code
    ON finance.payroll_run (run_code)
    WHERE run_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS finance.payroll_result_line (
    id BIGSERIAL PRIMARY KEY,
    payroll_employee_result_id BIGINT NOT NULL REFERENCES finance.payroll_employee_result(id) ON DELETE CASCADE,
    line_type VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS finance.payroll_result_allocation (
    id BIGSERIAL PRIMARY KEY,
    payroll_employee_result_id BIGINT NOT NULL REFERENCES finance.payroll_employee_result(id) ON DELETE CASCADE,
    outlet_id BIGINT NOT NULL,
    work_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    allocated_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS finance.payroll_contract_snapshot (
    id BIGSERIAL PRIMARY KEY,
    payroll_run_id BIGINT NOT NULL REFERENCES finance.payroll_run(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    employment_type VARCHAR(20) NOT NULL,
    salary_type VARCHAR(20) NOT NULL,
    base_salary NUMERIC(18, 2) NOT NULL,
    tax_code VARCHAR(50),
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_finance_payroll_contract_snapshot UNIQUE (payroll_run_id, contract_id)
);

CREATE TABLE IF NOT EXISTS finance.payroll_attendance_snapshot (
    id BIGSERIAL PRIMARY KEY,
    payroll_run_id BIGINT NOT NULL REFERENCES finance.payroll_run(id) ON DELETE CASCADE,
    payroll_employee_result_id BIGINT REFERENCES finance.payroll_employee_result(id) ON DELETE SET NULL,
    approval_id BIGINT NOT NULL,
    shift_assignment_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    contract_id BIGINT,
    business_date DATE NOT NULL,
    attendance_status VARCHAR(20),
    work_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    overtime_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_finance_payroll_attendance_snapshot UNIQUE (payroll_run_id, approval_id)
);
