-- ============================================================
-- V20: POS Table Permissions
-- Adds pos.table.read, pos.table.write and pos.table.manage
-- and assigns them to operational POS roles.
-- ============================================================

INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES (
    'pos.table.read',
    'POS table read',
    'View dining table state and assignments',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES (
    'pos.table.write',
    'POS table write',
    'Create and update dining tables',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES (
    'pos.table.manage',
    'POS table manage',
    'Manage dining table operational status',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'pos.table.read',
    'pos.table.write',
    'pos.table.manage'
)
WHERE role.code IN ('bootstrap_admin', 'region_manager', 'outlet_manager', 'staff')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Bump policy version so cached principals pick up new table permissions.
UPDATE iam.policy_version_state
SET version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;
