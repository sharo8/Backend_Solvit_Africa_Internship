-- If /api/attendance returns 500 with "Unknown column" in the server log, run these statements
-- against your MySQL database (skip any line that errors with "Duplicate column name").

ALTER TABLE attendances ADD COLUMN duration_minutes INT NULL;
ALTER TABLE attendances ADD COLUMN is_excused TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE attendances ADD COLUMN excuse_reason VARCHAR(500) NULL;
