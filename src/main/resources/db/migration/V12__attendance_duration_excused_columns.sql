-- Align `attendances` with JPA entity (Admin attendance API). Safe to re-run logic via INFORMATION_SCHEMA.
-- Fixes500s from SQLGrammarException when columns are missing (e.g. DB created before Hibernate added them).

SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'duration_minutes') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN duration_minutes INT NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'is_excused') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN is_excused TINYINT(1) NOT NULL DEFAULT 0'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'excuse_reason') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN excuse_reason VARCHAR(500) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
