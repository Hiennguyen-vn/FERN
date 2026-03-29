CREATE SCHEMA IF NOT EXISTS hr_master;

CREATE TABLE IF NOT EXISTS hr_master.employee_profile (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    user_account_id BIGINT,
    full_name VARCHAR(150) NOT NULL,
    dob DATE,
    gender VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    national_id VARCHAR(30),
    email VARCHAR(150),
    phone VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hired_at DATE,
    terminated_at DATE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hr_master_employee_profile_gender_ck CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT hr_master_employee_profile_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT hr_master_employee_profile_dates_ck CHECK (terminated_at IS NULL OR hired_at IS NULL OR terminated_at >= hired_at)
);

CREATE TABLE IF NOT EXISTS hr_master.employee_contract (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES hr_master.employee_profile(id) ON DELETE CASCADE,
    employment_type VARCHAR(20) NOT NULL,
    salary_type VARCHAR(20) NOT NULL,
    base_salary NUMERIC(18, 2) NOT NULL,
    region_id BIGINT,
    tax_code VARCHAR(50),
    contract_status VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hr_master_employee_contract_type_ck CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'SEASONAL', 'CONTRACTOR')),
    CONSTRAINT hr_master_employee_contract_salary_ck CHECK (salary_type IN ('MONTHLY', 'DAILY', 'HOURLY')),
    CONSTRAINT hr_master_employee_contract_status_ck CHECK (contract_status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT hr_master_employee_contract_dates_ck CHECK (base_salary >= 0 AND (end_date IS NULL OR end_date >= start_date))
);

CREATE TABLE IF NOT EXISTS hr_master.employee_bank_account (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES hr_master.employee_profile(id) ON DELETE CASCADE,
    bank_name VARCHAR(150) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    branch_name VARCHAR(150),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hr_master_employee_status ON hr_master.employee_profile (status);
CREATE INDEX IF NOT EXISTS idx_hr_master_contract_employee ON hr_master.employee_contract (employee_id, start_date);
