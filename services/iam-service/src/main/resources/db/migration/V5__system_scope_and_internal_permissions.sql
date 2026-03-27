ALTER TABLE iam.user_scope_assignment
    ALTER COLUMN scope_id DROP NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'iam.user_scope_assignment'::regclass
          AND conname = 'iam_user_scope_assignment_uk'
    ) THEN
        ALTER TABLE iam.user_scope_assignment DROP CONSTRAINT iam_user_scope_assignment_uk;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_iam_user_scope_assignment_effective
    ON iam.user_scope_assignment (user_id, scope_type, COALESCE(scope_id, -1));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'iam.user_scope_assignment'::regclass
          AND conname = 'iam_user_scope_assignment_scope_ck'
    ) THEN
        ALTER TABLE iam.user_scope_assignment
            ADD CONSTRAINT iam_user_scope_assignment_scope_ck
            CHECK (
                (scope_type = 'SYSTEM' AND scope_id IS NULL)
                OR (scope_type IN ('REGION', 'OUTLET') AND scope_id IS NOT NULL)
            );
    END IF;
END $$;

INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES
    ('inventory.internal.reserve', 'Reserve inventory internally', 'Reserve stock for internal service flows', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role (code, name, description, status, created_at, updated_at)
VALUES
    ('product_manager', 'Product Manager', 'Catalog master operator with system scope', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('system_admin', 'System Admin', 'IAM and audit administrator with system scope', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

DELETE FROM iam.role_permission
WHERE role_id IN (
    SELECT id
    FROM iam.role
    WHERE code IN ('staff', 'outlet_manager', 'regional_finance', 'finance')
)
AND permission_id IN (
    SELECT id
    FROM iam.permission
    WHERE code IN (
        'catalog.internal.resolve',
        'org.scope.resolve',
        'inventory.internal.reserve',
        'catalog.product.read',
        'catalog.price.read'
    )
);

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'catalog.ingredient.read',
    'catalog.ingredient.write',
    'catalog.product.read',
    'catalog.product.write',
    'catalog.recipe.read',
    'catalog.recipe.write',
    'catalog.price.read',
    'catalog.price.write'
)
WHERE role.code = 'product_manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'iam.user.read',
    'iam.user.write',
    'iam.role.read',
    'iam.role.write',
    'iam.role.assign',
    'iam.scope.assign',
    'iam.permission.read',
    'iam.permission_override.read',
    'iam.permission_override.write',
    'audit.read',
    'audit.detail.read',
    'audit.export'
)
WHERE role.code = 'system_admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'catalog.internal.resolve',
    'org.scope.resolve',
    'inventory.internal.reserve'
)
WHERE role.code = 'bootstrap_admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM iam.role_permission
WHERE role_id = (SELECT id FROM iam.role WHERE code = 'staff')
  AND permission_id IN (
      SELECT id
      FROM iam.permission
      WHERE code IN ('catalog.product.read', 'catalog.price.read')
  );

INSERT INTO iam.user_scope_assignment (user_id, scope_type, scope_id, created_at)
SELECT 1, 'SYSTEM', NULL, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM iam.user_scope_assignment
    WHERE user_id = 1 AND scope_type = 'SYSTEM' AND scope_id IS NULL
);
