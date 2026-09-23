-- ============================================================
-- 001 - Baseline marker
--
-- The initial schema is applied from ../schema/schema.sql
-- before any numbered migration runs (see run_migrations.ps1).
-- This file is a no-op that records the baseline in
-- schema_migrations for auditability.
-- ============================================================

SELECT '001_baseline applied (schema.sql is canonical baseline)' AS applied;