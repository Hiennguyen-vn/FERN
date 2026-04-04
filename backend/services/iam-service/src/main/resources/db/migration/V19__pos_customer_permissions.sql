-- ============================================================
-- V19: POS Customer Permissions
-- Adds pos.customer.read and pos.customer.write permissions
-- and assigns them to relevant roles.
-- ============================================================

-- Insert permission: pos.customer.read
INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES (
    'pos.customer.read',
    'POS customer read',
    'View customer information',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO NOTHING;

-- Insert permission: pos.customer.write
INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES (
    'pos.customer.write',
    'POS customer write',
    'Create and update customer information',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO NOTHING;

-- Grant pos.customer.read to: bootstrap_admin, region_manager, outlet_manager, staff
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code = 'pos.customer.read'
WHERE role.code IN ('bootstrap_admin', 'region_manager', 'outlet_manager', 'staff')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Grant pos.customer.write to: bootstrap_admin, region_manager, outlet_manager, staff
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code = 'pos.customer.write'
WHERE role.code IN ('bootstrap_admin', 'region_manager', 'outlet_manager', 'staff')
ON CONFLICT (role_id, permission_id) DO NOTHING;
