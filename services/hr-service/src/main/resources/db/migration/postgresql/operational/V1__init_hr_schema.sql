CREATE SCHEMA IF NOT EXISTS hr;

CREATE TABLE IF NOT EXISTS hr.employee_assignment (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    position_title VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hr_employee_assignment_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    CONSTRAINT hr_employee_assignment_dates_ck CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE IF NOT EXISTS hr.shift_schedule (
    id BIGSERIAL PRIMARY KEY,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    shift_name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    published_at TIMESTAMPTZ,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hr_shift_schedule_status_ck CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS hr.shift_assignment (
    id BIGSERIAL PRIMARY KEY,
    shift_schedule_id BIGINT NOT NULL REFERENCES hr.shift_schedule(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL,
    assigned_role VARCHAR(100),
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hr_shift_assignment_uk UNIQUE (shift_schedule_id, employee_id),
    CONSTRAINT hr_shift_assignment_attendance_ck CHECK (attendance_status IN ('PENDING', 'PRESENT', 'LATE', 'ABSENT', 'LEAVE')),
    CONSTRAINT hr_shift_assignment_approval_ck CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS hr.attendance_event (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    shift_assignment_id BIGINT REFERENCES hr.shift_assignment(id),
    event_type VARCHAR(20) NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    source_system VARCHAR(50),
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hr_attendance_event_type_ck CHECK (event_type IN ('CLOCK_IN', 'CLOCK_OUT', 'BREAK_START', 'BREAK_END'))
);

CREATE TABLE IF NOT EXISTS hr.attendance_approval (
    id BIGSERIAL PRIMARY KEY,
    shift_assignment_id BIGINT NOT NULL REFERENCES hr.shift_assignment(id) ON DELETE CASCADE,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    comments TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hr_attendance_approval_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS hr.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT hr_outbox_status_ck CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_hr_employee_assignment_route ON hr.employee_assignment (region_id, outlet_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_hr_shift_schedule_route ON hr.shift_schedule (region_id, outlet_id, shift_date);
CREATE INDEX IF NOT EXISTS idx_hr_attendance_event_route ON hr.attendance_event (region_id, outlet_id, event_time);
