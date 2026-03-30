CREATE TABLE IF NOT EXISTS hr_master.employee_contract_guard (
    employee_id BIGINT PRIMARY KEY REFERENCES hr_master.employee_profile(id) ON DELETE CASCADE,
    touched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
