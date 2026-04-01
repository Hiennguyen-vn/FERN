-- V11__bootstrap_admin_full_permissions.sql
-- =============================================================================
-- H-03 Fix: Ensure bootstrap_admin role always has ALL permissions.
-- The V1 migration only granted permissions that existed at init time.
-- Permissions added in V4–V10 (inventory, hr.attendance, etc.) were NOT
-- automatically included. This migration back-fills the gap.
-- =============================================================================
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 1, p.id, CURRENT_TIMESTAMP
FROM iam.permission p
WHERE NOT EXISTS (
    SELECT 1 FROM iam.role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id
);

-- Bump policy version so cached principals get refreshed on next request.
UPDATE iam.policy_version_state SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = 1;
