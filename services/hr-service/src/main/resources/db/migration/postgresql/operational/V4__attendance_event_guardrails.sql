ALTER TABLE hr.attendance_event
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_attendance_event_idempotency_key
    ON hr.attendance_event (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_hr_attendance_event_outlet_time
    ON hr.attendance_event (outlet_id, event_time DESC, id);

CREATE INDEX IF NOT EXISTS idx_hr_attendance_event_employee_time
    ON hr.attendance_event (employee_id, event_time DESC, id);

CREATE INDEX IF NOT EXISTS idx_hr_attendance_event_shift_time
    ON hr.attendance_event (shift_assignment_id, event_time, id);

ALTER TABLE hr.shift_assignment
    DROP CONSTRAINT IF EXISTS hr_shift_assignment_attendance_ck;

ALTER TABLE hr.shift_assignment
    ADD CONSTRAINT hr_shift_assignment_attendance_ck
    CHECK (attendance_status IN ('PENDING', 'PRESENT', 'LATE', 'ABSENT'));
