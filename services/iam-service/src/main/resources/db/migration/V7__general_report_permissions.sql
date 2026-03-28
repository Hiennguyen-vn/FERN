INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES
    ('report.read', 'Read reports', 'Read reporting facts and summaries', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('report.export', 'Export reports', 'Export reporting datasets', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'report.read',
    'report.export'
)
WHERE role.code IN ('regional_finance', 'finance')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 1, permission.id, CURRENT_TIMESTAMP
FROM iam.permission permission
WHERE permission.code IN (
    'report.read',
    'report.export'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;
