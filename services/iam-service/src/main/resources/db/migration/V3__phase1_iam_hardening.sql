INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES
    ('iam.permission_override.read', 'Read permission overrides', 'Read direct permission overrides for users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('iam.permission_override.write', 'Write permission overrides', 'Manage direct permission overrides for users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('audit.read', 'Read audit records', 'Read audit events and request traces', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('audit.detail.read', 'Read audit details', 'Read sensitive audit detail payloads', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('audit.export', 'Export audit records', 'Export audit records within allowed scope', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.ingredient.read', 'Read ingredients', 'Read catalog ingredient data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.ingredient.write', 'Write ingredients', 'Create and update catalog ingredient data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.product.read', 'Read products', 'Read catalog product data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.product.write', 'Write products', 'Create and update catalog product data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.recipe.read', 'Read recipes', 'Read catalog recipe data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.recipe.write', 'Write recipes', 'Create and update catalog recipe data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.price.read', 'Read prices', 'Read catalog price and tax data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.price.write', 'Write prices', 'Create and update catalog price and tax data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('catalog.internal.resolve', 'Resolve catalog internals', 'Resolve menu, price and recipe data for internal services', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 1, permission.id, CURRENT_TIMESTAMP
FROM iam.permission permission
WHERE permission.code IN (
    'iam.permission_override.read',
    'iam.permission_override.write',
    'audit.read',
    'audit.detail.read',
    'audit.export',
    'catalog.ingredient.read',
    'catalog.ingredient.write',
    'catalog.product.read',
    'catalog.product.write',
    'catalog.recipe.read',
    'catalog.recipe.write',
    'catalog.price.read',
    'catalog.price.write',
    'catalog.internal.resolve'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_iam_auth_session_refresh_token_hash
    ON iam.auth_session (refresh_token_hash);
