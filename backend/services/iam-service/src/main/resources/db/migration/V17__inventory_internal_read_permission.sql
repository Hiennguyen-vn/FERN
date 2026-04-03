INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES (
    'inventory.internal.read',
    'Inventory internal read',
    'Internal service-to-service inventory reads',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code = 'inventory.internal.read'
WHERE role.code = 'bootstrap_admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;
