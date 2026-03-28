INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES
    ('hr.employee.read', 'Read employees', 'Read employee profiles', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.employee.write', 'Write employees', 'Create and update employee profiles', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.contract.read', 'Read contracts', 'Read employee contracts', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.contract.write', 'Write contracts', 'Create and update employee contracts', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.shift.read', 'Read shifts', 'Read shift schedules and assignments', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.shift.write', 'Write shifts', 'Create shift schedules and assignments', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.attendance.write', 'Write attendance', 'Record attendance events', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.attendance.review', 'Review attendance', 'Approve and reject attendance', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.payroll.prepare', 'Prepare payroll', 'Prepare and submit payroll inputs', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.internal.read', 'HR internal read', 'Internal service-to-service HR reads', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('finance.payroll.read', 'Read payroll', 'Read payroll runs and periods', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('finance.payroll.prepare', 'Prepare payroll finance', 'Create payroll periods and draft runs', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('finance.payroll.approve', 'Approve payroll', 'Approve and reject payroll runs', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('finance.payroll.pay', 'Pay payroll', 'Mark payroll runs as paid', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('finance.config.read', 'Read finance config', 'Read finance numbering and policy config', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('finance.config.write', 'Write finance config', 'Write finance numbering and policy config', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('report.payroll.read', 'Read payroll reports', 'Read payroll summary and detail reports', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('report.payroll.export', 'Export payroll reports', 'Export payroll reports', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role (code, name, description, status, created_at, updated_at)
VALUES
    ('hr', 'HR', 'Company HR operator for employee, contract and payroll preparation', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'hr.employee.read',
    'hr.employee.write',
    'hr.contract.read',
    'hr.contract.write',
    'hr.shift.read',
    'hr.shift.write',
    'hr.attendance.write',
    'hr.attendance.review',
    'hr.payroll.prepare',
    'finance.payroll.read',
    'finance.payroll.prepare'
)
WHERE role.code = 'hr'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'hr.shift.read',
    'hr.shift.write',
    'hr.attendance.write',
    'hr.attendance.review'
)
WHERE role.code = 'outlet_manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'finance.payroll.read',
    'report.payroll.read',
    'report.payroll.export'
)
WHERE role.code = 'regional_finance'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'finance.payroll.read',
    'finance.payroll.prepare',
    'finance.payroll.approve',
    'finance.payroll.pay',
    'finance.config.read',
    'finance.config.write',
    'report.payroll.read',
    'report.payroll.export'
)
WHERE role.code = 'finance'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 1, permission.id, CURRENT_TIMESTAMP
FROM iam.permission permission
WHERE permission.code IN (
    'hr.employee.read',
    'hr.employee.write',
    'hr.contract.read',
    'hr.contract.write',
    'hr.shift.read',
    'hr.shift.write',
    'hr.attendance.write',
    'hr.attendance.review',
    'hr.payroll.prepare',
    'hr.internal.read',
    'finance.payroll.read',
    'finance.payroll.prepare',
    'finance.payroll.approve',
    'finance.payroll.pay',
    'finance.config.read',
    'finance.config.write',
    'report.payroll.read',
    'report.payroll.export'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;
