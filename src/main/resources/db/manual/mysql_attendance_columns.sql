-- If /api/attendance returns 500 with "Unknown column" in the server log, prefer Flyway
-- migration V12__attendance_duration_excused_columns.sql (runs automatically on startup).
-- Otherwise run these statements against MySQL (skip lines that error with "Duplicate column name").

ALTER TABLE attendances ADD COLUMN duration_minutes INT NULL;
ALTER TABLE attendances ADD COLUMN is_excused TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE attendances ADD COLUMN excuse_reason VARCHAR(500) NULL;
