INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'catalog.product.read',
    'catalog.price.read',
    'pos.session.read',
    'pos.session.open'
)
WHERE role.code = 'staff'
ON CONFLICT (role_id, permission_id) DO NOTHING;
