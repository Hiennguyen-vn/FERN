INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES
    ('finance.payroll.detail.read', 'Read payroll detail', 'Read employee-level payroll detail', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hr.contract.detail.read', 'Read contract detail', 'Read sensitive employee contract detail', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'finance.payroll.detail.read',
    'hr.contract.detail.read'
)
WHERE role.code = 'hr'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code = 'finance.payroll.detail.read'
WHERE role.code = 'finance'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 1, permission.id, CURRENT_TIMESTAMP
FROM iam.permission permission
WHERE permission.code IN (
    'finance.payroll.detail.read',
    'hr.contract.detail.read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;
