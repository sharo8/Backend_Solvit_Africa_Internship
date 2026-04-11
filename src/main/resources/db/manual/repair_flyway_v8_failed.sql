-- Unblock startup when Flyway reports:
--   "Detected failed migration to version 8 (task role management enrichment)"
--
-- Steps:
-- 1) Connect to the same database as the app (see spring.datasource.url).
-- 2) Run this script once.
-- 3) Restart the app (Flyway will re-run V8; V8 is idempotent if columns already exist).
--
-- Optional (CLI): mvn -pl backend flyway:repair
--   (requires flyway-maven-plugin with same datasource config)

DELETE FROM flyway_schema_history WHERE version = '8' AND success = 0;
