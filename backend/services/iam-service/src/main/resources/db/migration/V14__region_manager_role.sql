INSERT INTO iam.role (code, name, description, status, created_at, updated_at)
VALUES (
    'region_manager',
    'Region Manager',
    'Regional oversight operator for outlet visibility, revenue, inventory and payroll summaries within region subtree',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'org.region.read',
    'org.outlet.read',
    'pos.session.read',
    'pos.order.read',
    'inventory.balance.read',
    'inventory.ledger.read',
    'report.read',
    'report.payroll.read'
)
WHERE role.code = 'region_manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;
