-- Idempotent adds for attendances columns expected by JPA (beyond V12). Fixes 500s when an old schema
-- predates Hibernate ddl-auto updates or ALTER was never applied.

SET @db := DATABASE();

-- manual_entry
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'manual_entry') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN manual_entry TINYINT(1) NOT NULL DEFAULT 0'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- modified_by_user_id
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'modified_by_user_id') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN modified_by_user_id BIGINT NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- validated_by
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'validated_by') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN validated_by BIGINT NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- validated_at
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'validated_at') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN validated_at DATETIME(6) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- created_at
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'created_at') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN created_at DATETIME(6) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- updated_at
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'updated_at') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN updated_at DATETIME(6) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- latitude
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'latitude') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN latitude VARCHAR(50) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- longitude
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'longitude') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN longitude VARCHAR(50) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- check_in_location
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'check_in_location') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN check_in_location VARCHAR(500) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- check_out_location
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'check_out_location') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN check_out_location VARCHAR(500) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- notes
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'attendances' AND COLUMN_NAME = 'notes') > 0,
    'SELECT 1',
    'ALTER TABLE attendances ADD COLUMN notes VARCHAR(1000) NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
