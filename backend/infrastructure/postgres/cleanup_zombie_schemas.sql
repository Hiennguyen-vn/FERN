-- ============================================================================
-- FERN Database Cleanup: Remove zombie schemas from fern_master
-- ============================================================================
-- After restructuring, the following schemas were moved OUT of fern_master:
--   - raw_events, report  → fern_reporting (report-service)
--   - audit                → fern_reporting (audit-service)
--   - finance_projection   → fern_reporting (finance-service projection)
--
-- If you previously ran the system before the database split, these schemas
-- and their tables/data still exist in fern_master as zombies.
-- This script drops them from fern_master ONLY.
--
-- ⚠️  IMPORTANT: Run this script ONLY against the fern_master database!
--     psql -U fern -d fern_master -f cleanup_zombie_schemas.sql
-- ============================================================================

\echo '=== Checking for zombie schemas in fern_master ==='

-- 1. Drop raw_events schema (now in fern_reporting, owned by report-service)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'raw_events') THEN
        RAISE NOTICE 'Dropping zombie schema: raw_events';
        DROP SCHEMA raw_events CASCADE;
    ELSE
        RAISE NOTICE 'Schema raw_events does not exist — OK';
    END IF;
END $$;

-- 2. Drop report schema (now in fern_reporting, owned by report-service)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'report') THEN
        RAISE NOTICE 'Dropping zombie schema: report';
        DROP SCHEMA report CASCADE;
    ELSE
        RAISE NOTICE 'Schema report does not exist — OK';
    END IF;
END $$;

-- 3. Drop audit schema (now in fern_reporting, owned by audit-service)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'audit') THEN
        RAISE NOTICE 'Dropping zombie schema: audit';
        DROP SCHEMA audit CASCADE;
    ELSE
        RAISE NOTICE 'Schema audit does not exist — OK';
    END IF;
END $$;

-- 4. Drop finance_projection schema (now in fern_reporting, owned by finance-service)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'finance_projection') THEN
        RAISE NOTICE 'Dropping zombie schema: finance_projection';
        DROP SCHEMA finance_projection CASCADE;
    ELSE
        RAISE NOTICE 'Schema finance_projection does not exist — OK';
    END IF;
END $$;

\echo '=== Zombie schema cleanup complete ==='
\echo 'Remaining schemas in fern_master:'
SELECT schema_name FROM information_schema.schemata 
WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
ORDER BY schema_name;
