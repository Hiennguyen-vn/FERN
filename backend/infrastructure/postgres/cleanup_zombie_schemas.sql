-- ============================================================================
-- FERN Database Cleanup: Remove obsolete fern_reporting database
-- ============================================================================
-- Current runtime topology keeps reporting, audit, notification, and finance
-- projection schemas inside fern_master. The separate fern_reporting database
-- is obsolete and should be removed if it still exists.
--
-- Safe to run against postgres or fern_master:
--   psql -U fern -d postgres -f cleanup_zombie_schemas.sql
--   psql -U fern -d fern_master -f cleanup_zombie_schemas.sql
-- ============================================================================

\echo '=== Current application databases before cleanup ==='
SELECT datname
FROM pg_database
WHERE datistemplate = false
ORDER BY datname;

\echo '=== Terminating fern_reporting sessions if present ==='
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'fern_reporting'
  AND pid <> pg_backend_pid();

\echo '=== Dropping obsolete database fern_reporting if it exists ==='
DROP DATABASE IF EXISTS fern_reporting;

\echo '=== Current application databases after cleanup ==='
SELECT datname
FROM pg_database
WHERE datistemplate = false
ORDER BY datname;
