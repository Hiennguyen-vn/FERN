INSERT INTO iam.role (code, name, description, status, created_at, updated_at)
VALUES ('audit_viewer', 'Audit Viewer', 'Audit-only reviewer with system scope', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'audit.read',
    'audit.detail.read',
    'audit.export'
)
WHERE role.code = 'audit_viewer'
ON CONFLICT (role_id, permission_id) DO NOTHING;
