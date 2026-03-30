CREATE TABLE IF NOT EXISTS hr.shift_assignment_guard (
    employee_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    touched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (employee_id, shift_date)
);
