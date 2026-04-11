-- Projects may exist without a cohort; assignment is driven by tasks.
-- Skip if `projects` is created later by Hibernate (empty DB + ddl-auto).
SET @exist := (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'projects');
SET @sql := IF(@exist > 0, 'ALTER TABLE projects MODIFY COLUMN group_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
