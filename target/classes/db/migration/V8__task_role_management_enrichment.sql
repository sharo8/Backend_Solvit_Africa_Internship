-- Task workflow enrichment. TEXT columns avoid MySQL InnoDB row-size limits.
-- Idempotent: safe to re-run after a failed attempt (Flyway repair + migrate).

SET @db := DATABASE();

-- instructions
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'instructions';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN instructions TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- evidence_url
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'evidence_url';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN evidence_url TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- evidence_notes
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'evidence_notes';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN evidence_notes TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- supervisor_comment
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'supervisor_comment';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN supervisor_comment TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- rejection_reason
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'rejection_reason';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN rejection_reason TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- estimated_hours
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'estimated_hours';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN estimated_hours INT NULL', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- submission_count
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'submission_count';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN submission_count INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- expected_evidence_type
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'expected_evidence_type';
SET @s := IF(@c = 0, 'ALTER TABLE tasks ADD COLUMN expected_evidence_type VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
