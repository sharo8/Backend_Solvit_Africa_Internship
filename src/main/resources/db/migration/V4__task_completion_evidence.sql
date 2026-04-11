ALTER TABLE tasks ADD COLUMN completion_evidence VARCHAR(4000) NULL;
ALTER TABLE tasks ADD COLUMN same_day_incomplete_reminder_sent TINYINT(1) NOT NULL DEFAULT 0;
