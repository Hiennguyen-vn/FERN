DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.attendance_fact'::regclass
          AND conname = 'uk_attendance_fact_source_event'
    ) THEN
        ALTER TABLE report.attendance_fact DROP CONSTRAINT uk_attendance_fact_source_event;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.payroll_fact'::regclass
          AND conname = 'uk_payroll_fact_source_event'
    ) THEN
        ALTER TABLE report.payroll_fact DROP CONSTRAINT uk_payroll_fact_source_event;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'report.expense_fact'::regclass
          AND conname = 'uk_expense_fact_source_event'
    ) THEN
        ALTER TABLE report.expense_fact DROP CONSTRAINT uk_expense_fact_source_event;
    END IF;
END $$;

ALTER TABLE report.expense_fact
    ADD COLUMN IF NOT EXISTS payroll_run_id BIGINT,
    ADD COLUMN IF NOT EXISTS employee_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_attendance_fact_source_key
    ON report.attendance_fact (source_event_id, employee_id, business_date);

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_payroll_fact_source_key
    ON report.payroll_fact (source_event_id, employee_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_expense_fact_source_key
    ON report.expense_fact (source_event_id, expense_record_id);
